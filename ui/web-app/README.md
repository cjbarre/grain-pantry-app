# shadow-cljs + UIx + shadcn Starter

## Dependencies
Run `npm install`

## Development
Run `npm run dev`

This starts the development server on port 8080 with hot reload.

## Release Build

Build the production version:
```bash
npm run build
```

This will:
1. Compile TypeScript shadcn components
2. Build and minify CSS with TailwindCSS
3. Compile ClojureScript with advanced optimizations

## Testing Production Build

Serve the production files with SPA routing support:
```bash
npx serve public -p 8080 -s
```

The production build outputs optimized static files to the `public/` directory ready for deployment.