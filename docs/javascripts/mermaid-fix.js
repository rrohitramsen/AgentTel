// Fix Mermaid node text colors in light mode.
// Material for MkDocs overrides inline color styles set in Mermaid diagrams.
// This script runs after Mermaid renders and forces white text on dark nodes.

function fixMermaidColors() {
  var scheme = document.body.getAttribute("data-md-color-scheme");
  if (scheme !== "default") return; // only fix light mode

  document.querySelectorAll(".mermaid .node .nodeLabel").forEach(function(el) {
    el.style.setProperty("color", "#fff", "important");
  });
  document.querySelectorAll(".mermaid .node .label div").forEach(function(el) {
    el.style.setProperty("color", "#fff", "important");
  });
  document.querySelectorAll(".mermaid .node .label span").forEach(function(el) {
    el.style.setProperty("color", "#fff", "important");
  });
  // SVG text elements
  document.querySelectorAll(".mermaid .node text").forEach(function(el) {
    el.style.setProperty("fill", "#fff", "important");
  });
  document.querySelectorAll(".mermaid .node tspan").forEach(function(el) {
    el.style.setProperty("fill", "#fff", "important");
  });
}

// Run after initial page load
if (document.readyState === "loading") {
  document.addEventListener("DOMContentLoaded", function() {
    setTimeout(fixMermaidColors, 500);
    setTimeout(fixMermaidColors, 1500);
  });
} else {
  setTimeout(fixMermaidColors, 500);
  setTimeout(fixMermaidColors, 1500);
}

// Run after Material's instant navigation loads new pages
if (typeof document$ !== "undefined") {
  document$.subscribe(function() {
    setTimeout(fixMermaidColors, 500);
    setTimeout(fixMermaidColors, 1500);
  });
}

// Also observe for dynamically inserted mermaid SVGs
var observer = new MutationObserver(function(mutations) {
  for (var i = 0; i < mutations.length; i++) {
    if (mutations[i].addedNodes.length) {
      setTimeout(fixMermaidColors, 200);
      break;
    }
  }
});
observer.observe(document.body, { childList: true, subtree: true });
