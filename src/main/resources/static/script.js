
console.log("URL Shortener JavaScript loaded");

const shortenForm = document.getElementById("shortenForm");
const originalUrlInput = document.getElementById("originalUrl");
const shortenButton = document.getElementById("shortenButton");
const resultSection = document.getElementById("result");
const shortUrlInput = document.getElementById("shortUrl");
const copyButton = document.getElementById("copyButton");
const message = document.getElementById("message");

// Create a short URL when the form is submitted
shortenForm.addEventListener("submit", async function (event) {
    event.preventDefault();

    const originalUrl = originalUrlInput.value.trim();

    if (!originalUrl) {
        showMessage("Please enter a URL.");
        return;
    }

    shortenButton.disabled = true;
    shortenButton.textContent = "Shortening...";
    resultSection.classList.add("hidden");
    showMessage("");

    try {
        const response = await fetch("/api/urls", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            // IMPORTANT: ShortenRequest expects "url", not "originalUrl"
            body: JSON.stringify({
                url: originalUrl
            })
        });

        // Read the response safely, even if the server returns non-JSON
        const responseText = await response.text();
        let data = {};

        try {
            data = responseText ? JSON.parse(responseText) : {};
        } catch {
            throw new Error("The server returned an unexpected response.");
        }

        if (!response.ok) {
            const validationMessage =
                data.message ||
                data.detail ||
                data.error ||
                (data.errors && JSON.stringify(data.errors)) ||
                responseText ||
                "Unable to shorten this URL.";

            throw new Error(validationMessage);
        }

        // Support common response field names
        const shortCode =
            data.shortCode ||
            data.short_code ||
            data.code;

        if (!shortCode) {
            throw new Error(
                "URL created, but no short code was found in the API response. Check ShortenResponse.java."
            );
        }

        const shortUrl = new URL(
            "/" + encodeURIComponent(shortCode),
            window.location.origin
        ).toString();

        shortUrlInput.value = shortUrl;
        resultSection.classList.remove("hidden");
        showMessage("URL shortened successfully!");

    } catch (error) {
        console.error("Shortening failed:", error);
        showMessage(error.message || "Something went wrong.");

    } finally {
        shortenButton.disabled = false;
        shortenButton.textContent = "Shorten";
    }
});

// Copy the generated short URL
copyButton.addEventListener("click", async function () {
    if (!shortUrlInput.value) {
        showMessage("Create a short URL first.");
        return;
    }

    try {
        await navigator.clipboard.writeText(shortUrlInput.value);
        showMessage("Short URL copied!");

    } catch (error) {
        // Fallback for browsers where clipboard access is unavailable
        shortUrlInput.select();
        shortUrlInput.setSelectionRange(0, shortUrlInput.value.length);

        const copied = document.execCommand("copy");

        showMessage(
            copied
                ? "Short URL copied!"
                : "Please copy the URL manually."
        );
    }
});

// Display status and error messages
function showMessage(text) {
    message.textContent = text;
}
