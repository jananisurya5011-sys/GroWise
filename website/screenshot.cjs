const puppeteer = require('puppeteer');
const fs = require('fs');

(async () => {
  const browser = await puppeteer.launch();
  const page = await browser.newPage();
  
  await page.setViewport({ width: 1280, height: 800 });
  await page.goto('http://localhost:5173/login', { waitUntil: 'networkidle0' });
  
  try {
    console.log("Checking for login...");
    const emailInput = await page.$('input[placeholder*="email"]');
    if (emailInput) {
      await page.type('input[placeholder*="email"]', 'farmer@test.com');
      await page.type('input[type="password"]', 'password123');
      await page.click('button.primary');
      await page.waitForNavigation({ waitUntil: 'networkidle0' });
    }
  } catch(e) {
    console.log("Login check failed or not needed.");
  }

  await page.goto('http://localhost:5173/deals', { waitUntil: 'networkidle0' });
  await new Promise(r => setTimeout(r, 2000));

  const html = await page.evaluate(() => document.body.innerHTML);
  fs.writeFileSync('C:/Users/ranjith kumar/.gemini/antigravity-ide/brain/037d3faa-27a9-49b9-83b5-b8d5c5b5cd00/deals_dump.html', html);
  
  const chatCard = await page.$('div[style*="cursor: pointer"]');
  if (chatCard) {
      await chatCard.click();
      await new Promise(r => setTimeout(r, 3000));
      const html2 = await page.evaluate(() => document.body.innerHTML);
      fs.writeFileSync('C:/Users/ranjith kumar/.gemini/antigravity-ide/brain/037d3faa-27a9-49b9-83b5-b8d5c5b5cd00/chat_dump.html', html2);
  } else {
      console.log("No chat cards found");
  }

  console.log("Done");
  await browser.close();
})();
