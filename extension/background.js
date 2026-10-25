// Autofill is disabled until extension authentication and origin binding are implemented.
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
    if (request.type === 'MAP_FIELDS') {
        sendResponse({ success: false, message: 'Browser autofill is not available yet. Apply directly on the employer website.' });
    }
});
