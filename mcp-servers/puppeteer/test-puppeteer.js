const puppeteer = require('puppeteer');

(async () => {
  console.log('Testing Puppeteer installation...');
  
  try {
    const browser = await puppeteer.launch({
      headless: true,
      executablePath: '/snap/bin/chromium',
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-dev-shm-usage']
    });
    
    const page = await browser.newPage();
    await page.goto('https://example.com');
    const title = await page.title();
    
    console.log('✓ Successfully launched browser');
    console.log('✓ Page title:', title);
    
    await browser.close();
    console.log('✓ Browser closed successfully');
    console.log('\nPuppeteer is working correctly!');
  } catch (error) {
    console.error('✗ Error:', error.message);
    process.exit(1);
  }
})();