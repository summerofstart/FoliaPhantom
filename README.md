
# FoliaPhantom

**日本語 | Japanese**

**FoliaPhantom** は、既存の Bukkit / Spigot / Paper プラグインを Folia（Paper の ThreadedRegions 対応バージョン）でも動作可能にする、軽量な「ゴースト・ラッパー」です。

---

## 🧩 特徴

- 任意の外部プラグインを Folia 環境向けに自動適応
- `plugin.yml` に `folia-supported: true` を自動追加
- BukkitScheduler を Folia に対応したプロキシへ差し替え
- 非同期・リージョン同期スケジューリング両対応
- Jar の差し替え・再生成処理を自動で実施
- 複数プラグインの並列ラップに対応
- Bukkit/Paper の同期的なチャンクAPI呼び出し（`getChunkAt`, `loadChunk`, `regenerateChunk`等）を、Folia対応の非同期処理やリージョンベースの処理に内部的に変換・実行（実験的機能）

---

## ⚙️ インストール手順

1. `FoliaPhantom.jar` を `plugins` フォルダに配置
2. サーバーを起動 → 自動で `config.yml` が生成されます
3. `plugins/FoliaPhantom/config.yml` を開いて対象プラグインを登録：

```yaml
wrapped-plugins:
  - name: ExamplePlugin
    original-jar-path: plugins/ExamplePlugin.jar
    patched-jar-path: plugins/Folia/ExamplePlugin-patched.jar
    folia-enabled: true
````

4. サーバーを再起動（または `reload`）で自動ラップ＆有効化されます

---

## ⚠️ 制限・注意

* 一部の **NMS（net.minecraft.server）依存コード** には未対応です
  ➜ 特に `WorldServer`, `MinecraftServer`, `EntityPlayer` などを直接扱うプラグインは正常動作しない場合があります。
* `Unsafe` を用いたリフレクションで `BukkitScheduler` をフックしています。セキュリティ制限のある環境では動作しない可能性があります。
* すべての Folia 非対応プラグインを補償するものではありません。
* **チャンクラッパー機能 (実験的):**
    * 同期的なチャンクAPI呼び出しのFolia対応変換は、すべてのケースで完璧に動作するとは限りません。特定のプラグインやAPIの使われ方によっては、依然として問題が発生する可能性があります。
    * この機能はサーバー内部の `World` オブジェクトをプロキシし、一部NMSコードに依存する可能性のある方法で実現されています。Minecraftのバージョンアップにより互換性が失われるリスクがあります。
    * 同期APIを非同期APIで無理にラップするため、チャンク操作のパフォーマンスが低下する場合があります。特に多数のチャンクを一度に操作するような処理では影響が大きくなる可能性があります。
    * `Player.getWorld()` で取得される `World` オブジェクトは、現バージョンではプロキシされないため、この方法で取得したワールドに対するチャンク操作はラップされません。`Bukkit.getWorld()` 等で取得したワールドはプロキシ対象です。
    * Folia API の今後の変更によって、このラッパー機能が影響を受ける可能性があります。
    * 複雑な処理のため、予期せぬエラー（特にチャンクアクセス関連）が発生する可能性があります。問題発生時はサーバーログを確認してください。

---

## 📂 config.yml オプション

| キー名                 | 説明                                      |
| ------------------- | --------------------------------------- |
| `name`              | 任意の識別名（ログなどに表示）                         |
| `original-jar-path` | 元のプラグイン JAR の相対パス                       |
| `patched-jar-path`  | Folia 用にパッチを施した JAR の保存先                |
| `folia-enabled`     | `true` なら Folia パッチを適用、`false` ならそのまま使用 |

---

## 📜 ライセンス

MIT License

---

## ✉️ お問い合わせ

バグ報告・改善提案などは GitHub Issues または Discord でどうぞ。

---

---

# FoliaPhantom

**English | 英語**

**FoliaPhantom** is a lightweight "ghost wrapper" plugin that enables running legacy Bukkit / Spigot / Paper plugins in the Folia (ThreadedRegions) environment by dynamically adapting them.

---

## 🧩 Features

* Automatically wraps and adapts legacy plugins for Folia
* Injects `folia-supported: true` into `plugin.yml` if missing
* Replaces BukkitScheduler with a Folia-compatible proxy
* Supports async and region-based scheduling
* Re-generates and patches plugin JARs as needed
* Supports multiple wrapped plugins simultaneously
* Converts synchronous Bukkit/Paper chunk API calls (e.g., `getChunkAt`, `loadChunk`, `regenerateChunk`) to Folia-compatible asynchronous or region-based operations internally (Experimental).

---

## ⚙️ Installation

1. Place `FoliaPhantom.jar` in your server's `plugins/` folder.
2. Start the server once to generate `config.yml`.
3. Edit `plugins/FoliaPhantom/config.yml`:

```yaml
wrapped-plugins:
  - name: ExamplePlugin
    original-jar-path: plugins/ExamplePlugin.jar
    patched-jar-path: plugins/Folia/ExamplePlugin-patched.jar
    folia-enabled: true
```

4. Restart your server – wrapped plugins will be automatically loaded and enabled.

---

## ⚠️ Limitations

* **NMS (net.minecraft.server)** based plugins are **partially unsupported**.
  ➜ If a plugin directly interacts with internals like `WorldServer`, `EntityPlayer`, or `MinecraftServer`, it may not function properly.
* This plugin uses `Unsafe` and reflection to intercept `BukkitScheduler`. It may not work in JVM environments with strict security settings.
* It does **not guarantee compatibility** with all Folia-unsupported plugins.
* **Chunk Wrapping Feature (Experimental):**
    * The conversion of synchronous chunk API calls for Folia compatibility may not work perfectly in all scenarios. Issues might still arise depending on the specific plugin or how the API is used.
    * This feature proxies server's internal `World` objects and may rely on NMS code, risking incompatibility with Minecraft version updates.
    * Forcing synchronous behavior on top of asynchronous Folia APIs can lead to performance degradation in chunk operations, especially when handling many chunks at once.
    * `World` objects obtained via `Player.getWorld()` are not currently proxied by this feature. Chunk operations on such `World` instances will not be wrapped. Worlds obtained via `Bukkit.getWorld()` are targeted for proxying.
    * Future changes to the Folia API might affect this wrapper's functionality.
    * Due to the complexity, unexpected errors (especially related to chunk access) may occur. Check server logs if issues arise.

---

## 📂 config.yml Options

| Key                 | Description                                            |
| ------------------- | ------------------------------------------------------ |
| `name`              | Identifier (used in logs)                              |
| `original-jar-path` | Path to the original plugin JAR                        |
| `patched-jar-path`  | Destination for the Folia-patched JAR                  |
| `folia-enabled`     | If `true`, apply Folia patching; if `false`, use as-is |

---

## 📜 License

MIT License

---

## ✉️ Contact

For bug reports or feedback, please use GitHub Issues or contact us via Discord.




---

## 🔓 ソースコード公開について（予定）

このプラグインのソースコードは現在非公開ですが、後日 GitHub 上で **オープンソース（MITライセンス）として公開予定**です。\\

---

## 🔓 Source Code Release (Planned)

The source code for this plugin is currently **not public**, but we plan to release it as **open source (MIT License)** on GitHub in the near future.\\



# ✨ Decompilation Permission / デコンパイル許可


# 🛠 Let’s build it together. / 一緒に作りましょう！

