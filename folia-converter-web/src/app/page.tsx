"use client"; // This directive is necessary for using React hooks

import { useState, FormEvent, ChangeEvent } from 'react';
import Image from "next/image";

export default function Home() {
  const [file, setFile] = useState<File | null>(null);
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);

  const handleFileChange = (event: ChangeEvent<HTMLInputElement>) => {
    const files = event.target.files;
    if (files && files.length > 0) {
      const selectedFile = files[0];
      if (selectedFile.name.endsWith('.jar') || selectedFile.type === 'application/java-archive') {
        setFile(selectedFile);
        setError(null); // Clear previous errors
        setSuccessMessage(null); // Clear previous success messages
      } else {
        setFile(null);
        setError('Invalid file type. Please select a .jar file.');
        setSuccessMessage(null);
      }
    } else {
      setFile(null);
    }
  };

  const handleSubmit = async (event: FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    if (!file) {
      setError('Please select a file to convert.');
      return;
    }

    setIsLoading(true);
    setError(null);
    setSuccessMessage(null);

    const formData = new FormData();
    formData.append('jarFile', file);

    try {
      const response = await fetch('/api/convert', {
        method: 'POST',
        body: formData,
      });

      if (response.ok) {
        const blob = await response.blob();
        const downloadUrl = window.URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = downloadUrl;
        const originalFileName = file.name;
        // Try to get filename from Content-Disposition header if available
        const disposition = response.headers.get('Content-Disposition');
        let fileName = `patched-${originalFileName}`; // Default filename
        if (disposition && disposition.indexOf('attachment') !== -1) {
            const filenameRegex = /filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/;
            const matches = filenameRegex.exec(disposition);
            if (matches != null && matches[1]) {
                fileName = matches[1].replace(/['"]/g, '');
            }
        }
        a.download = fileName;
        document.body.appendChild(a);
        a.click();
        a.remove();
        window.URL.revokeObjectURL(downloadUrl);
        setSuccessMessage(`Successfully converted and downloaded ${fileName}!`);
      } else {
        const errorData = await response.json();
        setError(errorData.error || 'Conversion failed. Please try again.');
      }
    } catch (err) {
      console.error('An error occurred during conversion:', err);
      setError('An unexpected error occurred. Check the console for details.');
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <main className="flex min-h-screen flex-col items-center justify-start p-6 md:p-12 bg-gradient-to-br from-slate-900 to-slate-800 text-slate-100">
      <div className="w-full max-w-2xl">
        <header className="mb-10 text-center">
          <div className="inline-flex items-center justify-center gap-3 mb-4">
            <Image
              src="/next.svg" // Assuming you have next.svg in public, or replace with a relevant icon
              alt="Converter Icon"
              className="dark:invert"
              width={50}
              height={50}
              priority
            />
            <h1 className="text-4xl font-bold tracking-tight bg-clip-text text-transparent bg-gradient-to-r from-sky-400 to-blue-500">
              Folia JAR Converter
            </h1>
          </div>
          <p className="text-slate-400 text-lg">
            Upload your Spigot/Paper plugin JAR to make it Folia compatible.
          </p>
        </header>

        <form onSubmit={handleSubmit} className="space-y-8 p-8 bg-slate-800/70 rounded-xl shadow-2xl backdrop-blur-md border border-slate-700">
          <div>
            <label
              htmlFor="jarFile"
              className="block mb-2 text-sm font-medium text-sky-300"
            >
              Select JAR file to convert:
            </label>
            <input
              type="file"
              id="jarFile"
              name="jarFile"
              accept=".jar,application/java-archive"
              onChange={handleFileChange}
              className="block w-full text-sm text-slate-300 border border-slate-600 rounded-lg cursor-pointer bg-slate-700/50 file:mr-4 file:py-3 file:px-4 file:rounded-l-lg file:border-0 file:text-sm file:font-semibold file:bg-sky-500 file:text-sky-50 hover:file:bg-sky-600 focus:outline-none focus:ring-2 focus:ring-sky-500 focus:border-sky-500"
            />
            {file && <p className="mt-2 text-xs text-slate-400">Selected: {file.name}</p>}
          </div>

          {error && (
            <div className="p-3 rounded-md bg-red-500/20 border border-red-500/50 text-red-300 text-sm">
              <p>Error: {error}</p>
            </div>
          )}
          {successMessage && (
            <div className="p-3 rounded-md bg-green-500/20 border border-green-500/50 text-green-300 text-sm">
              <p>{successMessage}</p>
            </div>
          )}

          <button
            type="submit"
            disabled={isLoading || !file}
            className="w-full px-6 py-3.5 text-base font-semibold text-white bg-gradient-to-r from-sky-500 to-blue-600 rounded-lg shadow-md hover:from-sky-600 hover:to-blue-700 focus:outline-none focus:ring-2 focus:ring-sky-400 focus:ring-offset-2 focus:ring-offset-slate-900 disabled:opacity-50 disabled:cursor-not-allowed transition-all duration-150 ease-in-out"
          >
            {isLoading ? (
              <div className="flex items-center justify-center">
                <svg className="animate-spin -ml-1 mr-3 h-5 w-5 text-white" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                  <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                </svg>
                Converting...
              </div>
            ) : (
              'Convert to Folia Compatible JAR'
            )}
          </button>
        </form>
        <footer className="mt-12 text-center text-sm text-slate-500">
            <p>Powered by Next.js and a sprinkle of Java magic.</p>
            <p>Ensure your server environment has Java installed to use the converter.</p>
        </footer>
      </div>
    </main>
  );
}
