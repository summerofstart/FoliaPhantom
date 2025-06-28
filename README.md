
# FoliaPhantom v2.0

**日本語 | Japanese**

**FoliaPhantom v2.0** は、既存の Bukkit / Spigot / Paper プラグインを Folia（Paper の ThreadedRegions 対応バージョン）でも動作可能にすることを目指す、軽量な「ゴースト・ラッパー」です。**v2.0では設定ファイル不要のコンフィグレス動作に刷新されました。**

---

## 🧩 特徴 (v2.0)

-   **コンフィグレス:** 設定ファイルなしで、多くのプラグインを Folia 環境に適応させます。
-   **API変換による自動適応:** 主要な Bukkit API（ワールド操作、チャンク操作、エンティティ操作、ブロック操作など）の呼び出しを検出し、Foliaの適切なスレッドで実行されるように内部的にラップします。
    -   この変換は、互換性を最大限に重視し、**元のメソッドの見た目（シグネチャ）を変更しません。**
    -   内部的には非同期処理を行いますが、呼び出し元には同期的に結果を返します。
-   **スケジューラのプロキシ:** 従来の `BukkitScheduler` API呼び出しを Folia 対応のスケジューラ呼び出しに自動的に振り分けます。
-   **NMS非依存:** 基本的にNMSコードには触れず、Bukkit APIレベルでの互換性を提供します。

---

## ⚙️ インストール手順 (v2.0)

1.  `FoliaPhantom.jar` をサーバーの `plugins` フォルダに配置してください。
2.  サーバーを起動すると、FoliaPhantom が自動的に有効になり、他のプラグインのAPI呼び出しの変換を開始します。
    -   **設定ファイル (`config.yml`) は不要です。**

---

## ⚠️ 制限・注意 (v2.0)

*   **NMS（net.minecraft.server）依存コード:** このプラグインは主に Bukkit API を対象としています。NMSコード（例: `WorldServer`, `MinecraftServer`, `EntityPlayer` の直接操作）を多用するプラグインは、引き続き正常に動作しない可能性があります。
*   **スケジューラフック:** `Unsafe` を用いたリフレクションで `BukkitScheduler` をフックしています。特殊なセキュリティ設定やJava環境では動作しない可能性があります。
*   **バイトコード操作のリスク:**
    *   FoliaPhantom は実行時に他のプラグインのコード（正確にはBukkit API呼び出し部分）を書き換えます（バイトコード操作）。これは強力な技術ですが、他の高度なバイトコード操作を行うツール（一部のパフォーマンス計測ツールや他の互換性レイヤープラグインなど）と稀に競合する可能性があります。
    *   全てのプラグイン、全ての状況での完全な動作を保証するものではありません。
*   **パフォーマンスに関する考慮事項:**
    *   API変換は互換性を最優先し、Foliaの非同期処理の結果を内部で同期的に待ってから元の処理に返します。これにより、一部のAPI呼び出しではメインスレッドで短時間の待機が発生する可能性があり、サーバーのパフォーマンスに影響を与える場合があります。多くの場合は問題にならないレベルですが、極端に頻繁な呼び出しや重い処理では影響が顕在化するかもしれません。
*   **互換性の限界:** FoliaPhantomは多くの一般的なケースで互換性を提供することを目指していますが、プラグインの内部実装によっては予期せぬ問題が発生する可能性もゼロではありません。

---

## 📜 ライセンス

MIT License

---

## ✉️ お問い合わせ

バグ報告・改善提案などは GitHub Issues または Discord でどうぞ。

---

---

# FoliaPhantom v2.0

**English | 英語**

**FoliaPhantom v2.0** is a lightweight "ghost wrapper" plugin aiming to enable existing Bukkit / Spigot / Paper plugins to run in the Folia (Paper's ThreadedRegions enabled version) environment. **Version 2.0 has been revamped for configless operation.**

---

## 🧩 Features (v2.0)

-   **Configless:** Adapts many plugins to the Folia environment without needing configuration files.
-   **Automatic Adaptation via API Transformation:** Detects calls to major Bukkit APIs (such as world manipulation, chunk operations, entity interactions, block changes) and internally wraps them to be executed on appropriate Folia threads.
    -   This transformation prioritizes maximum compatibility and **does not change the apparent signature of the original methods.**
    -   Internally, it performs asynchronous operations but returns results synchronously to the caller.
-   **Scheduler Proxy:** Automatically redirects traditional `BukkitScheduler` API calls to Folia-compatible scheduler calls.
-   **NMS-Independent:** Primarily provides compatibility at the Bukkit API level, without touching NMS code.

---

## ⚙️ Installation (v2.0)

1.  Place `FoliaPhantom.jar` into your server's `plugins` folder.
2.  Start your server. FoliaPhantom will automatically activate and begin transforming API calls from other plugins.
    -   **No configuration file (`config.yml`) is required.**

---

## ⚠️ Limitations & Caveats (v2.0)

*   **NMS (net.minecraft.server) Dependent Code:** This plugin primarily targets the Bukkit API. Plugins heavily relying on NMS code (e.g., direct manipulation of `WorldServer`, `MinecraftServer`, `EntityPlayer`) may still not function correctly.
*   **Scheduler Hooking:** Uses `Unsafe` and reflection to hook into `BukkitScheduler`. May not work in specific Java environments with strict security settings.
*   **Bytecode Manipulation Risks:**
    *   FoliaPhantom modifies the code of other plugins at runtime (specifically, Bukkit API call sites) via bytecode manipulation. While powerful, this technique may rarely conflict with other tools that perform advanced bytecode operations (e.g., some performance profilers or other compatibility layer plugins).
    *   It does not guarantee perfect operation with all plugins under all circumstances.
*   **Performance Considerations:**
    *   API transformations prioritize compatibility by synchronously waiting for the results of internal asynchronous operations before returning to the original caller. This may cause brief blocking waits on the main thread for some API calls, potentially impacting server performance. While often negligible, this could become noticeable with extremely frequent calls or heavy operations.
*   **Compatibility Limits:** While FoliaPhantom aims to provide compatibility for many common cases, unexpected issues may still arise depending on a plugin's internal implementation.

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

