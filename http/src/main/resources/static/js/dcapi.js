let currentDcApiTab = null;

function invokeDigitalCredentialsApi(button) {
    const url = button && button.dataset ? button.dataset.dcapiUrl : null;
    if (!url) {
        console.warn("DC API URL missing on button element.");
        return;
    }
    currentDcApiTab = button.closest("[data-dcapi-container]");
    fetch(url)
        .then((response) => response.json())
        .then(async (payload) => {
            console.log("Received DC API payload:", payload);

            if (typeof DigitalCredential === "undefined" || !navigator.credentials) {
                console.warn("Digital Credentials API not supported by this user agent.");
                showDcApiStatus("Digital Credentials API not supported by this browser.", false);
                return;
            }
            const requests = payload?.digital?.requests;
            if (requests == null || requests.length !== 1) {
                showDcApiStatus("Requests object malformed", false);
                return;
            }
            const unsupported = requests
                .map((request) => request && request.protocol)
                .filter((protocol) => !DigitalCredential.userAgentAllowsProtocol(protocol));
            if (unsupported.length > 0) {
                console.warn("Digital Credentials API protocol not allowed:", unsupported.join(", "));
                showDcApiStatus("The protocol is unsupported.", false);
                return;
            }
            try {
                const result = await navigator.credentials.create(payload);
                // Some browsers (Chrome on Desktop) add their own container around the wallet response; therefore, we may encounter two data objects.
                const status = result?.data?.status ?? result?.data?.data?.status ?? "Unknown";
                const isSuccess = status === "offer_accepted";
                showDcApiStatus(status, isSuccess);
            } catch (error) {
                console.error("Error issuing digital credential:", error);
                showDcApiStatus(error, false);
            }
        })
        .catch((error) => {
            console.error("Failed to load DC API payload:", error);
            showDcApiStatus("Failed to load Digital Credentials API payload.", false);
        });
}

function showDcApiStatus(status, isSuccess) {
    const container = currentDcApiTab;
    const resultEl = container ? container.querySelector("[data-dcapi-result]") : null;
    const messageEl = container ? container.querySelector("[data-dcapi-message]") : null;
    const messageTextEl = container ? container.querySelector("[data-dcapi-message-text]") : null;
    const reloadBtn = container ? container.querySelector("[data-dcapi-reload]") : null;
    if (!resultEl) {
        return;
    }
    if (container) {
        container.classList.add("dcapi-consumed");
    }
    resultEl.classList.remove("d-none");
    if (messageEl) {
        messageEl.classList.remove("alert-success", "alert-danger");
        messageEl.classList.add(isSuccess ? "alert-success" : "alert-danger");
    }
    if (messageTextEl) {
        messageTextEl.textContent = isSuccess
            ? "Credential offer accepted."
            : "Credential offer failed. Status: " + status;
    }

    if (reloadBtn) {
        reloadBtn.classList.remove("d-none");
    }
}

function reloadRequest() {
    window.location.reload();
}
