/**
 * Simple utility to read URL fragment on initial page load.
 * Returns the current hash without the '#' prefix.
 */
window.getUrlFragment = function() {
    return window.location.hash.substring(1);
};