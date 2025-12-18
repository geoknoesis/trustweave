# Website Customization Guide

This guide explains how to customize the TrustWeave website and landing page if you fork this repository.

## Overview

The TrustWeave website consists of:
- **Landing Page** (`index.html`) - Main marketing website
- **Documentation** (`docs/`) - Jekyll-based documentation site
- **Partnership Page** (`partnership.html`) - Partnership information

## Customizing the Landing Page

### 1. Update Branding

**File**: `index.html`

- **Logo/Company Name** (line ~405): Update the logo text
  ```html
  <div class="logo">YourCompanyName</div>
  ```

- **Footer** (line ~624): Update company attribution
  ```html
  <p class="footer-text">Made with ❤️ by Your Company</p>
  ```

### 2. Update GitHub Links

**File**: `index.html`

Replace all instances of:
- `https://github.com/geoknoesis/TrustWeave` with your repository URL
- Search for "geoknoesis" and replace with your GitHub username/organization

**Locations to update:**
- Navigation GitHub button (line ~411)
- Hero section "Get Started" button (line ~422)
- Open Source section links (lines ~575, ~580, ~585)
- Footer links (line ~620)

### 3. Update Company Links

**File**: `index.html`

- **Footer** (line ~623): Update Geoknoesis LLC link
  ```html
  <a href="https://www.yourcompany.com" target="_blank">Your Company</a>
  ```

### 4. Customize SaaS Waitlist Form

**File**: `index.html`

The waitlist form uses Formspree for email collection. To set it up:

1. **Create a Formspree account**: Go to https://formspree.io
2. **Create a new form** and get your form ID
3. **Update the form action** (line ~598):
   ```html
   <form ... action="https://formspree.io/f/YOUR_FORM_ID" method="POST">
   ```
   Replace `YOUR_FORM_ID` with your actual Formspree form ID

4. **Optional**: Remove the waitlist section entirely if you don't need it:
   - Remove the entire `<section class="cta-section" id="waitlist">` block (lines ~592-613)

### 5. Update Documentation Links

**File**: `index.html`

- **Navigation** (line ~409): Update documentation link if your docs are hosted elsewhere
- **Footer** (line ~621): Update documentation link

## Customizing Documentation

### 1. Update Documentation Configuration

**File**: `docs/_config.yml`

- **Title and Description** (lines 3-5): Update with your project name
- **Author** (line 4): Update with your name/company
- **GitHub Links** (lines 40-42): Update repository URLs
- **Navigation**: Customize the navigation structure as needed

### 2. Update Documentation Content

All documentation files are in the `docs/` directory. You can:
- Modify existing markdown files
- Add new documentation pages
- Update the navigation in `_config.yml`

### 3. Customize Documentation Styling

**File**: `docs/assets/css/custom.scss`

Modify the SCSS file to match your brand colors and styling preferences.

## Removing Commercial Elements

If you want to remove the SaaS waitlist section:

1. **Remove the waitlist section** from `index.html` (lines ~592-613)
2. **Remove the waitlist link** from navigation (line ~410)
3. **Update hero buttons** to remove "Join SaaS Waitlist" button (line ~423)

## GitHub Pages Deployment

### Option 1: Deploy from `/docs` folder (Recommended)

1. Go to your repository Settings → Pages
2. Set Source to "Deploy from a branch"
3. Select branch: `main`
4. Select folder: `/docs`
5. Click Save

### Option 2: Use GitHub Actions

The repository includes a GitHub Actions workflow (`.github/workflows/deploy.yml`) that automatically builds and deploys the Jekyll site when you push to the main branch.

## Local Development

To test the documentation site locally:

```bash
cd docs
bundle install
bundle exec jekyll serve
```

Then visit `http://localhost:4000/docs/` in your browser.

## Email Service Setup

### Formspree (Current Implementation)

1. Sign up at https://formspree.io
2. Create a new form
3. Get your form ID
4. Update `index.html` line 598 with your form ID

### Alternative: EmailJS

If you prefer EmailJS:

1. Sign up at https://www.emailjs.com
2. Create an email service and template
3. Update the form JavaScript in `index.html` to use EmailJS SDK

### Alternative: Custom Backend

You can replace the form submission with your own backend API:

1. Update the form action to point to your API endpoint
2. Modify the JavaScript in `index.html` to handle your API response format

## Questions?

If you have questions about customizing the website, please:
- Check the [TrustWeave documentation](docs/)
- Open an issue on GitHub
- Contact the maintainers

