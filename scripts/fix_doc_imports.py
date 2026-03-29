"""One-off: normalize obsolete package paths in docs/*.md (excludes _site)."""
from __future__ import annotations

import pathlib

ROOT = pathlib.Path(__file__).resolve().parents[1] / "docs"
SKIP_DIGEST_FQN = {
    ROOT / "modules" / "trustweave-common-package-structure.md",
}


def main() -> None:
    for path in ROOT.rglob("*.md"):
        if "_site" in path.parts:
            continue
        text = path.read_text(encoding="utf-8")
        orig = text
        text = text.replace(
            "import org.trustweave.credential.wallet.Wallet\n",
            "import org.trustweave.wallet.Wallet\n",
        )
        text = text.replace(
            "import org.trustweave.credential.wallet.WalletDirectory\n",
            "import org.trustweave.wallet.WalletDirectory\n",
        )
        text = text.replace(
            "import org.trustweave.credential.wallet.CredentialFilter\n",
            "import org.trustweave.wallet.CredentialFilter\n",
        )
        text = text.replace(
            "import org.trustweave.spi.PluginLifecycle\n",
            "import org.trustweave.core.plugin.PluginLifecycle\n",
        )
        text = text.replace(
            "import org.trustweave.json.DigestUtils\n",
            "import org.trustweave.core.util.DigestUtils\n",
        )
        if path not in SKIP_DIGEST_FQN:
            text = text.replace(
                "org.trustweave.json.DigestUtils",
                "org.trustweave.core.util.DigestUtils",
            )
        if text != orig:
            path.write_text(text, encoding="utf-8")
            print(path.relative_to(ROOT))


if __name__ == "__main__":
    main()
