const puppeteer = require('puppeteer');

(async () => {
  const browser = await puppeteer.launch({
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  });
  
  const page = await browser.newPage();
  
  // Capture console logs
  page.on('console', msg => {
    const text = msg.text();
    if (text.includes('[MonitoringApp]') || text.includes('[LiveHistoryDisplay]') || text.includes('[StateTreeView]')) {
      console.log(text);
    }
  });
  
  await page.goto('http://localhost:3000');
  
  // Wait for the app to load and connect
  await page.waitForTimeout(3000);
  
  // Count IDLE states in the UI
  const idleStates = await page.evaluate(() => {
    const stateElements = Array.from(document.querySelectorAll('span')).filter(el => 
      el.textContent === 'IDLE' && 
      el.style.fontWeight === '500'
    );
    return stateElements.length;
  });
  
  console.log('\n=== UI Check ===');
  console.log('Number of IDLE states displayed:', idleStates);
  
  await browser.close();
})();
