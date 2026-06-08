// AI Job Pilot  - Background Service Worker
console.log("Job Pilot  Extension Installed");

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.type === 'MAP_FIELDS') {
        // Perform the fetch in the background to bypass CSP
        fetch('http://localhost:8080/api/v1/copilot/map-fields', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                resumeId: request.resumeId,
                fields: request.fields
            })
        })
        .then(response => response.json())
        .then(data => sendResponse(data))
        .catch(error => sendResponse({ success: false, error: error.message }));
        
        return true; // Keep message channel open for async response
    }
});
