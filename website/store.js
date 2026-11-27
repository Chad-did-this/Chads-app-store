const repository = "Chad-did-this/Chads-app-store";
const api = "https://api.github.com/repos/" + repository + "/releases?per_page=100";
const github = "https://github.com/" + repository;
const library = document.querySelector("#library");
const search = document.querySelector("#search");
const status = document.querySelector("#status");
let platform = null, catalog = [], releases = [], loaded = false, generation = 0;
const home = document.body.dataset.page === "home";
const make = (tag, className, value) => {
  const node = document.createElement(tag);
  if (className) node.className = className;
  if (value) node.textContent = value;
  return node;
};
function link(label, url) {
  const a = make("a", "", label);
  a.href = url;
  return a;
}
function latest(app) {
  return releases.filter(r => !r.draft && r.tag_name.startsWith(app.releasePrefix) && /^\d+\.\d+\.\d+$/.test(r.tag_name.slice(app.releasePrefix.length)) && r.assets.some(a => a.name === app.asset && a.size > 0 && a.browser_download_url.startsWith(github+"/releases/download/"))).sort((a,b)=>Number(b.tag_name.split(".").at(-1))-Number(a.tag_name.split(".").at(-1)))[0];
}
function renderHome() {
  for (const p of ["android","ios"]) {
    const app = catalog.find(a => a.id === p+"-store" && a.platform === p);
    if (!app) continue;
    const release = latest(app);
    const asset = release?.assets.find(a => a.name === app.asset && a.browser_download_url.startsWith(github+"/releases/download/"));
    if (!asset) continue;
    document.querySelector("#"+p+"-download").href = asset.browser_download_url;
    document.querySelector("#"+p+"-version").textContent = (p==="android"?"APK":"IPA")+" · v"+release.tag_name.slice(app.releasePrefix.length);
  }
}
function render() {
  if (home) { renderHome(); return; }
  if (!platform) return;
  const results = document.querySelector("#apps");
  results.replaceChildren();
  const query = search.value.toLowerCase().trim();
  for (const app of catalog.filter(a => a.platform === platform && !["android-store","ios-store"].includes(a.id) && (a.name+" "+a.category+" "+a.description).toLowerCase().includes(query))) {
    const release = latest(app);
    const asset = release?.assets.find(a => a.name === app.asset);
    const row = make("article","app");
    const info = make("div","app-info");
    info.append(make("h3","",app.name),make("p","",app.category+" · "+(platform==="android"?"Android APK":"iPhone IPA")),make("p","",app.description));
    if (release) info.append(make("p","",release.tag_name+" · "+(asset.size/1048576).toFixed(1)+" MB"));
    const action = asset ? link(platform==="android"?"Get APK":"Get IPA",asset.browser_download_url) : make("span","download disabled",loaded?"Not released":"Checking");
    if (asset) action.className = "download";
    row.append(make("div","app-icon","✦"),info,action);
    results.append(row);
  }
  if (!results.children.length && loaded) results.append(make("p","","No matching apps."));
}
async function load() {
  const request = ++generation;
  loaded = false; status.textContent = "Checking release availability…";
  render();
  try {
    let nextCatalog, nextReleases;
    try {
      const response = await fetch("release-feed.json?refresh="+Date.now(),{cache:"no-store"});
      if (!response.ok) throw Error("Feed unavailable");
      const feed = await response.json();
      if (!Array.isArray(feed.catalog) || !Array.isArray(feed.releases) || !Number.isFinite(feed.generated) || Date.now()/1000-feed.generated>86400) throw Error("Invalid or stale feed");
      nextCatalog = feed.catalog; nextReleases = feed.releases;
    } catch (error) {
      const [c,r] = await Promise.all([fetch("https://raw.githubusercontent.com/"+repository+"/main/docs/catalog.json?refresh="+Date.now(),{cache:"no-store"}),fetch(api,{cache:"no-cache"})]);
      if (!c.ok || !r.ok) throw Error("GitHub unavailable");
      nextCatalog = await c.json(); nextReleases = await r.json();
    }
    if (request !== generation) return;
    catalog = nextCatalog; releases = nextReleases;
    loaded = true; status.textContent = home ? "Latest available App Store versions checked." : "Showing published builds only.";
  } catch (error) {
    if (request !== generation) return;
    loaded = true; if (!home) releases = []; status.textContent = home ? "Latest version check unavailable. Published download links remain available." : "Could not verify releases. Refresh to try again.";
  }
  render();
}
function choose(next) {
  platform = next; library.hidden = false;
  library.classList.toggle("ios-library",next==="ios");
  for (const button of document.querySelectorAll("[data-platform]")) button.setAttribute("aria-pressed",String(button.dataset.platform===next));
  document.querySelector("#section-label").textContent = next==="android"?"ANDROID · APK":"APPLE · IPA";
  const guide = document.querySelector("#guide");
  guide.replaceChildren();
  if (next==="android") {
    guide.append(make("strong","","Android installation"),make("p","","Download an APK, open it, and approve Android’s install prompt. New builds use the permanent developer signing key. An installation from an earlier temporary-key build needs a one-time reinstall."),link("All releases ↗",github+"/releases"));
  } else {
    guide.append(make("strong","","iPhone installation"),make("p","","Downloading an IPA is not installation. Use a compatible SideStore or AltStore Classic setup to sign and install the app. Apple's account and device restrictions apply."),link("SideStore setup ↗","https://sidestore.io/"),link("AltStore Classic ↗","https://altstore.io/"),link("iOS source JSON ↗",github+"/releases/download/ios-source/source.json"));
  }
  const url = new URL(location.href); url.searchParams.set("platform",next); history.replaceState(null,"",url);
  render(); load();
}
if (home) {
  const requested = new URLSearchParams(location.search).get("platform");
  if (["android","ios"].includes(requested)) {
    const target = new URL("apps.html",location.href);
    target.searchParams.set("platform",requested);
    location.replace(target.href);
  } else {
    load();
  }
} else {
  document.querySelectorAll("[data-platform]").forEach(b=>b.addEventListener("click",()=>choose(b.dataset.platform)));
  search.addEventListener("input",render);
  document.querySelector("#refresh").addEventListener("click",load);
  const requested = new URLSearchParams(location.search).get("platform");
  if (["android","ios"].includes(requested)) choose(requested);
}
