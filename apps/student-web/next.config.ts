import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,

  // Internal workspace packages publish raw .ts/.tsx through their `exports`
  // field and have no build step, so Next must compile them itself. A package
  // missing from this list works under `next dev` and fails only at
  // `next build` -- keep every `packages/*` entry here.
    transpilePackages: ['@cybersixseven/ui', '@cybersixseven/api-client', '@cybersixseven/auth-client'],
};

export default nextConfig;
