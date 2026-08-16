const http = require('http');
const fs = require('fs');

const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'OPTIONS, POST');
    res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
    
    if (req.method === 'OPTIONS') {
        res.writeHead(200);
        res.end();
        return;
    }

    if (req.method === 'POST') {
        let body = '';
        req.on('data', chunk => {
            body += chunk.toString();
        });
        req.on('end', () => {
            fs.writeFileSync('C:/Users/ranjith kumar/.gemini/antigravity-ide/brain/037d3faa-27a9-49b9-83b5-b8d5c5b5cd00/telemetry.json', body);
            console.log("Telemetry received and saved.");
            res.writeHead(200);
            res.end('OK');
        });
    }
});

server.listen(9999, () => {
    console.log("Telemetry server running on port 9999");
});
