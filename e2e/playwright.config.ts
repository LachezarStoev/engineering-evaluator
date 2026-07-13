import {defineConfig} from '@playwright/test';
export default defineConfig({testDir:'./tests',fullyParallel:false,retries:1,use:{baseURL:process.env.BASE_URL||'http://localhost:8088',trace:'on-first-retry'},webServer:process.env.SKIP_WEB_SERVER?undefined:{command:'docker compose up --build',cwd:'..',url:'http://localhost:8088',reuseExistingServer:true,timeout:240_000}});
