# GitBook Configuration

This documentation is structured for GitBook. Here's how to set it up and use it.

## GitBook Setup

### Option 1: GitBook Cloud

1. Sign up at [gitbook.com](https://www.gitbook.com)
2. Create a new space
3. Connect your Git repository
4. GitBook will automatically detect `SUMMARY.md` and organize the documentation

### Option 2: GitBook CLI

1. Install GitBook CLI:
```bash
npm install -g gitbook-cli
```

2. Install dependencies:
```bash
cd docs
gitbook install
```

3. Serve locally:
```bash
gitbook serve
```

4. Build static site:
```bash
gitbook build
```

## File Structure

```
docs/
├── README.md              # Main documentation page
├── SUMMARY.md            # GitBook table of contents
├── introduction/         # Introduction section
├── getting-started/     # Getting started guides
├── core-concepts/       # Core concepts
├── modules/             # Module documentation
├── integrations/        # Integration modules
├── api-reference/      # API reference
├── examples/           # Code examples
├── advanced/          # Advanced topics
├── best-practices/    # Best practices
└── contributing/      # Contributing guide
```

## SUMMARY.md

The `SUMMARY.md` file defines the table of contents for GitBook. It uses markdown links to organize the documentation hierarchy.

## Customization

### GitBook Configuration

The `book.json` file in the `docs` directory contains the GitBook configuration:

```json
{
  "title": "TrustWeave Developer Documentation",
  "description": "Comprehensive developer documentation for TrustWeave",
  "author": "GeoKnoesis",
  "language": "en",
  "gitbook": ">=3.2.3",
  "root": "./docs",
  "structure": {
    "readme": "README.md",
    "summary": "SUMMARY.md"
  },
  "plugins": [
    "theme-default",
    "search",
    "livereload",
    "code",
    "copy-code-button",
    "expandable-chapters",
    "anchors",
    "github",
    "sharing",
    "fontsettings"
  ],
  "pluginsConfig": {
    "github": {
      "url": "https://github.com/geoknoesis/TrustWeave"
    }
  }
}
```

**Note**: GitBook.com (cloud) supports Mermaid diagrams natively - no plugin needed. Mermaid code blocks in markdown will be automatically rendered.

### Styling

Customize the theme by modifying CSS in `docs/styles/website.css`.

## Publishing

### GitBook Cloud

1. Push changes to your Git repository
2. GitBook will automatically rebuild
3. Your documentation will be available at your GitBook URL

### Static Site

Build and deploy the static site:

```bash
gitbook build docs _book
# Deploy _book directory to your hosting service
```

## Next Steps

- Review the [README.md](README.md) for documentation overview
- Check [Contributing](../contributing/README.md) for contribution guidelines

