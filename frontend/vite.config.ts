import { defineConfig } from 'vite';
   import react from '@vitejs/plugin-react';

   export default defineConfig({
     plugins: [react()],
     server: {
       proxy: {
         '/api': {
           target: 'http://localhost:8080',
           changeOrigin: true,
         },
       },
     },
     optimizeDeps: {
       include: ['leaflet', 'react-leaflet'], // Pre-bundle CJS deps
     },
     build: {
       commonjsOptions: {
         transformMixedEsModules: true, // Convert CJS to ESM
       },
     },
   });