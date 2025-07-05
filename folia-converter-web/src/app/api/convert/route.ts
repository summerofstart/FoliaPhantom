import { NextRequest, NextResponse } from 'next/server';
import fs from 'fs/promises';
import path from 'path';
import os from 'os';
import { execFile } from 'child_process';
import { promisify } from 'util';

const execFileAsync = promisify(execFile);

export async function POST(request: NextRequest) {
  try {
    const formData = await request.formData();
    const file = formData.get('jarFile') as File | null;

    if (!file) {
      return NextResponse.json({ error: 'No file uploaded.' }, { status: 400 });
    }

    if (file.type !== 'application/java-archive' && !file.name.endsWith('.jar')) {
        // クライアントサイドでもチェックしているが、念のためサーバーサイドでも確認
        return NextResponse.json({ error: 'Invalid file type. Please upload a JAR file.' }, { status: 400 });
    }

    const originalFileName = file.name;
    const tempDir = await fs.mkdtemp(path.join(os.tmpdir(), 'folia-converter-'));
    const originalJarPath = path.join(tempDir, originalFileName);
    const patchedJarPath = path.join(tempDir, `patched-${originalFileName}`);

    // Save uploaded file to a temporary location
    const fileBuffer = Buffer.from(await file.arrayBuffer());
    await fs.writeFile(originalJarPath, fileBuffer);

    // Path to the Java converter tool
    // Adjust this path according to your project structure and where the JAR is located
    const converterJarPath = path.resolve(process.cwd(), '../../FoliaPhantom/target/FoliaPhantom-1.0-SNAPSHOT.jar');
    // process.cwd() is /app/folia-converter-web, so we go up two levels to the repo root, then into FoliaPhantom.

    try {
      // Execute the Java JAR patcher
      console.log(`Executing: java -jar ${converterJarPath} ${originalJarPath} ${patchedJarPath}`);
      const { stdout, stderr } = await execFileAsync('java', [
        '-jar',
        converterJarPath,
        originalJarPath,
        patchedJarPath,
      ]);

      console.log('Java Patcher STDOUT:', stdout);
      if (stderr) {
        // Stderr might contain warnings or non-fatal info. Log it as a warning.
        console.warn('Java Patcher STDERR:', stderr);
      }

      // Verify that the patched file was actually created
      try {
        await fs.access(patchedJarPath);
      } catch (accessError) {
        // If patched file doesn't exist, this is a critical failure.
        // Prioritize stderr if it exists, otherwise use the access error.
        const errorDetails = stderr || (accessError instanceof Error ? accessError.message : String(accessError));
        throw new Error(`Patcher execution failed or patched file not found. Details: ${errorDetails}`);
      }

      // Read the patched file
      const patchedFileBuffer = await fs.readFile(patchedJarPath);

      // Send the patched file back to the client for download
      const headers = new Headers();
      headers.set('Content-Type', 'application/java-archive');
      headers.set('Content-Disposition', `attachment; filename="patched-${originalFileName}"`);

      return new NextResponse(patchedFileBuffer, { headers });

    } catch (error) {
      console.error('Error during JAR patching:', error);
      let errorMessage = 'Failed to convert JAR.';
      if (error instanceof Error) {
        errorMessage += ` Details: ${error.message}`;
        // Check if the error object might have stderr/stdout properties (from execFileAsync rejection)
        const execError = error as { stderr?: string; stdout?: string };
        if (execError.stderr) {
          errorMessage += ` Stderr: ${execError.stderr}`;
        }
        if (execError.stdout) { // stdout might also contain useful info on error
          errorMessage += ` Stdout: ${execError.stdout}`;
        }
      } else {
        errorMessage += ` Details: ${String(error)}`;
      }
      return NextResponse.json({ error: errorMessage }, { status: 500 });
    } finally {
      // Clean up temporary files and directory
      try {
        await fs.rm(tempDir, { recursive: true, force: true });
        console.log(`Cleaned up temporary directory: ${tempDir}`);
      } catch (cleanupError) {
        console.error('Error cleaning up temporary files:', cleanupError);
      }
    }
  } catch (error) {
    console.error('Error processing request:', error);
    let friendlyErrorMessage = 'An unexpected error occurred while processing your request.';
    if (error instanceof Error) {
        friendlyErrorMessage += ` Details: ${error.message}`
    } else {
        friendlyErrorMessage += ` Details: ${String(error)}`
    }
    return NextResponse.json({ error: friendlyErrorMessage }, { status: 500 });
  }
}
