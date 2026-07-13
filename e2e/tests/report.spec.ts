import {test,expect} from '@playwright/test';
test('employee sees transparent calculation and evidence-ready report',async({page,request})=>{
 await request.post('/api/v1/employees',{data:{email:'e2e@example.com',displayName:'E2E Developer',team:'Platform',currentLevelCode:'MID',targetLevelCode:'MID',employmentStart:'2026-01-01'}});
 await request.post('/api/v1/criteria',{data:{code:'E2E_VELOCITY',name:'Velocity',sourceTool:'jira',metricKey:'e2e_points',evaluationType:'AUTOMATIC',periodType:'QUARTER',operator:'>=',threshold:250,levelCode:'MID',version:1,status:'PUBLISHED',effectiveFrom:'2026-01-01'}});
 await request.post('/api/v1/evidence',{data:{email:'e2e@example.com',toolKey:'jira',metricKey:'e2e_points',externalId:`E2E-${Date.now()}`,occurredAt:'2026-07-10T10:00:00Z',value:260,title:'Feature',url:'https://jira/E2E-1'}});
 await request.post('/api/v1/evaluations/recalculate',{data:{email:'e2e@example.com',from:'2026-07-01',to:'2026-09-30',levelCode:'MID',ruleVersion:1}});
 await page.goto('/'); await page.getByPlaceholder('developer@company.com').fill('e2e@example.com'); await page.getByRole('button',{name:'Open report'}).click();
 await expect(page.getByText('E2E Developer')).toBeVisible(); await expect(page.getByText('PASS',{exact:true})).toBeVisible(); await expect(page.getByText(/SUM\(jira\.e2e_points\)/)).toBeVisible();
});
test('unknown employee receives a clear error',async({page})=>{await page.goto('/');await page.getByPlaceholder('developer@company.com').fill('missing@example.com');await page.getByRole('button',{name:'Open report'}).click();await expect(page.getByText(/Employee not found/)).toBeVisible();});
test('administrator can navigate configuration and integration screens',async({page})=>{await page.goto('/');await page.getByRole('button',{name:'Administration'}).click();await expect(page.getByRole('heading',{name:'Add employee'})).toBeVisible();await expect(page.getByRole('heading',{name:'Add criterion'})).toBeVisible();await page.getByRole('button',{name:'Integrations'}).click();await expect(page.getByRole('heading',{name:'Integration health'})).toBeVisible();});
