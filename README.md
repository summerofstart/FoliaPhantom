
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

