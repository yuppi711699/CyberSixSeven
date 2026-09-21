import type { NextConfig } from 'next';

const nextConfig: NextConfig = {
  reactStrictMode: true,
  transpilePackages: ['@cybersixseven/ui', '@cybersixseven/api-client', '@cybersixseven/auth-client'],
};

export default nextConfig;
