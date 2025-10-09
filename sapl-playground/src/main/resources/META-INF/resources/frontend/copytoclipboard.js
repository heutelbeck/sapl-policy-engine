window.copyToClipboard = (str) => {
  const textarea = document.createElement("textarea");
  textarea.value = str;
  textarea.style.position = "absolute";
  textarea.style.opacity = "0";
  document.body.appendChild(textarea);
  textarea.select();
  document.execCommand("copy");
  document.body.removeChild(textarea);
};