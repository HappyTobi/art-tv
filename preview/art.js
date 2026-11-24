/* Desktop design preview. Sony playback is handled only by the native Android app. */
var ArtTV = (function () {
  'use strict';
  var surface, panel, nav, picture, blurPicture, focus = 0, noticeTimer, panelRequest = 0, artRequest = 0;
  var navigationVisible = false, idleTimer = 0, wakeKey = 0, wakePointer = false;
  var mouseX = null, mouseY = null;
  function hideNavigation() {
    clearTimeout(idleTimer);
    idleTimer = 0;
    if (panel) return;
    navigationVisible = false;
    surface.className = 'art-surface';
    nav.className = 'art-nav art-nav-hidden';
    nav.setAttribute('aria-hidden', 'true');
    var buttons = nav.querySelectorAll('button');
    for (var i = 0; i < buttons.length; i++) {
      buttons[i].disabled = true;
      buttons[i].tabIndex = -1;
    }
    surface.setAttribute('role', 'button');
    surface.tabIndex = 0;
    surface.focus();
  }
  function navigationInteraction() {
    if (panel) return false;
    var waking = !navigationVisible;
    if (waking) {
      navigationVisible = true;
      surface.className = 'art-surface art-menu-visible';
      nav.className = 'art-nav';
      nav.setAttribute('aria-hidden', 'false');
      surface.setAttribute('role', 'group');
      surface.tabIndex = -1;
      var buttons = nav.querySelectorAll('button');
      for (var i = 0; i < buttons.length; i++) {
        buttons[i].disabled = false;
        buttons[i].tabIndex = 0;
      }
      buttons[focus].focus();
    }
    clearTimeout(idleTimer);
    if (!document.hidden) idleTimer = setTimeout(hideNavigation, 20000);
    return waking;
  }
  function pointerStart(event) {
    if (panel) return;
    wakePointer = navigationInteraction();
    if (wakePointer) {
      event.preventDefault();
      event.stopPropagation();
    }
  }
  function pointerMove(event) {
    if (event.clientX === mouseX && event.clientY === mouseY) return;
    mouseX = event.clientX;
    mouseY = event.clientY;
    navigationInteraction();
  }
  function pointerClick(event) {
    if (panel) return;
    if (wakePointer || navigationInteraction()) {
      wakePointer = false;
      event.preventDefault();
      event.stopPropagation();
    }
  }
  function get(key) {
    try { return localStorage.getItem('arttv.' + key) || ''; }
    catch (e) { return ''; }
  }
  function save(key, value) {
    try { localStorage.setItem('arttv.' + key, value); return true; }
    catch (e) { notice(tr('Unavailable')); return false; }
  }
  function node(tag, cls, parent, text) {
    var item = document.createElement(tag);
    if (cls) item.className = cls;
    if (text !== undefined) item.textContent = text;
    parent.appendChild(item);
    return item;
  }
  function button(parent, label, action) {
    var item = node('button', '', parent, label);
    item.type = 'button';
    item.onclick = function () {
      if (parent === nav && navigationInteraction()) return;
      action();
    };
    return item;
  }
  function notice(text) {
    var old = surface.querySelector('.art-notice');
    if (old) surface.removeChild(old);
    clearTimeout(noticeTimer);
    var message = node('div', 'art-notice', surface, text);
    message.setAttribute('role', 'status');
    noticeTimer = setTimeout(function () { if (message.parentNode) message.parentNode.removeChild(message); }, 5000);
  }
  function fit() {
    var scale = Math.min(window.innerWidth / 1920, window.innerHeight / 1080);
    document.getElementById('stage').style.webkitTransform =
      document.getElementById('stage').style.transform = 'scale(' + scale + ')';
  }
  function background() {
    var request = ++artRequest;
    var entries = window.ARTWORKS || [], selected = get('artBackground'), chosen = entries[0];
    for (var i = 0; i < entries.length; i++) if (entries[i].id === selected) chosen = entries[i];
    if (!chosen) { notice(tr('Background image') + ': ' + tr('Unavailable')); return; }
    var image = new Image();
    image.onload = function () {
      if (request !== artRequest) return;
      while (picture.firstChild) picture.removeChild(picture.firstChild);
      image.className = 'art-print';
      image.alt = chosen.title;
      picture.appendChild(image);
      picture.style.backgroundImage = 'url("art/' + chosen.file + '")';
      blurPicture.style.backgroundImage = picture.style.backgroundImage;
    };
    image.onerror = function () {
      if (request === artRequest) notice(tr('Background image') + ': ' + tr('Unavailable'));
    };
    image.src = 'art/' + chosen.file;
  }
  function closePanel() {
    panelRequest++;
    if (panel) surface.removeChild(panel);
    panel = null;
    navigationInteraction();
    nav.querySelectorAll('button')[focus].focus();
  }
  function createPanel(title) {
    panelRequest++;
    if (panel) surface.removeChild(panel);
    panel = null;
    hideNavigation();
    panel = node('div', 'art-panel', surface);
    surface.setAttribute('role', 'group');
    surface.tabIndex = -1;
    panel.setAttribute('role', 'dialog');
    panel.setAttribute('aria-modal', 'true');
    panel.setAttribute('aria-labelledby', 'art-panel-title');
    var window = node('div', 'art-window', panel);
    var header = node('div', 'art-panel-header', window);
    node('span', 'art-kicker', header, 'Art TV');
    var heading = node('h1', '', header, title);
    heading.id = 'art-panel-title';
    button(header, tr('Cancel'), closePanel).className = 'art-close';
    return window;
  }
  function launch(id) {
    TV.launch(id, null, function (ok) { if (!ok) notice(tr('Unavailable')); });
  }
  function liveTV() {
    var request = ++panelRequest;
    TV.inputs(function (items) {
      if (request !== panelRequest) return;
      for (var i = 0; i < items.length; i++) if (items[i].key === 'live') { launch(items[i].id); return; }
      notice(tr('Unavailable'));
    });
  }
  function inputs(open) {
    var request = ++panelRequest;
    TV.inputs(function (items) {
      if (request !== panelRequest) return;
      var hdmi = items.filter(function (item) { return item.key && item.key.indexOf('hdmi') === 0; });
      var selected = get('artInput');
      if (open) {
        for (var i = 0; i < hdmi.length; i++) if (hdmi[i].id === selected) { launch(selected); return; }
      }
      var window = createPanel('Apple TV · ' + tr('HDMI Inputs'));
      window.className += ' art-input-window';
      window.style.height = Math.min(700, Math.max(300, hdmi.length * 94 + 190)) + 'px';
      var list = node('div', 'art-input-list', window);
      if (!hdmi.length) node('h2', '', list, tr('No inputs'));
      hdmi.forEach(function (item) {
        var choice = button(list, (item.label || item.key.toUpperCase()) + (item.id === selected ? ' · ' + tr('On') : ''), function () {
          if (!save('artInput', item.id)) return;
          if (open) launch(item.id);
          closePanel();
        });
        choice.setAttribute('aria-pressed', item.id === selected ? 'true' : 'false');
      });
      (list.querySelector('button[aria-pressed="true"]') || list.querySelector('button') ||
        panel.querySelector('.art-close')).focus();
    });
  }
  function settings() {
    var windowPanel = createPanel(tr('Settings'));
    var sidebar = node('div', 'art-sidebar', windowPanel);
    var backgrounds = button(sidebar, tr('Background image'), function () {
      var chosen = gallery.querySelector('button[aria-pressed="true"]') || gallery.querySelector('button');
      if (chosen) chosen.focus();
    });
    backgrounds.className = 'art-section';
    button(sidebar, 'Apple TV · ' + tr('HDMI Inputs'), function () { inputs(false); });
    button(sidebar, tr('Live TV'), liveTV);
    if (TV.name === 'browser') button(sidebar, 'Sony · ' + tr('Home Screen'), function () { notice(tr('Unavailable')); });
    var content = node('div', 'art-content', windowPanel);
    node('h2', '', content, tr('Background image'));
    var gallery = node('div', 'art-gallery', content), entries = window.ARTWORKS || [];
    entries.forEach(function (entry, i) {
      var card = button(gallery, '', function () {
        if (!save('artBackground', entry.id)) return;
        background();
        closePanel();
      });
      var image = node('img', '', card);
      image.src = 'art/' + entry.file;
      image.alt = '';
      node('span', 'art-card-title', card, entry.title);
      var selected = entry.id === get('artBackground') || !get('artBackground') && i === 0;
      card.setAttribute('aria-pressed', selected ? 'true' : 'false');
      card.setAttribute('aria-label', entry.title + (selected ? ', ' + tr('On') : ''));
      var badge = node('span', 'art-selected', card, tr('On'));
      if (!selected) badge.style.visibility = 'hidden';
    });
    backgrounds.focus();
  }
  function settingsTarget(key) {
    var gallery = panel.querySelector('.art-gallery');
    if (!gallery) return null;
    var cards = gallery.querySelectorAll('button'), sidebar = panel.querySelectorAll('.art-sidebar button');
    var at = Array.prototype.indexOf.call(cards, document.activeElement);
    var close = panel.querySelector('.art-close');
    if (at >= 0) {
      if (key === 37) return at % 3 ? cards[at - 1] : sidebar[0];
      if (key === 39) return at % 3 < 2 && at + 1 < cards.length ? cards[at + 1] : cards[at];
      if (key === 38) return at >= 3 ? cards[at - 3] : close;
      return at + 3 < cards.length ? cards[at + 3] : cards[at];
    }
    at = Array.prototype.indexOf.call(sidebar, document.activeElement);
    var chosen = gallery.querySelector('button[aria-pressed="true"]') || cards[0] || sidebar[0];
    if (at >= 0) {
      if (key === 39) return chosen;
      if (key === 38) return at ? sidebar[at - 1] : close;
      if (key === 40) return sidebar[Math.min(at + 1, sidebar.length - 1)];
      return sidebar[at];
    }
    return key === 40 ? chosen : sidebar[0];
  }
  function keys(event) {
    wakePointer = false;
    var key = event.keyCode;
    var navigationKey = key === 9 || key === 13 || key === 32 || key === 37 || key === 38 || key === 39
      || key === 40 || key === 8 || key === 27 || key === 461 || key === 10009;
    if (!panel && (navigationKey || navigationVisible)) {
      if (wakeKey === key) {
        navigationInteraction();
        event.preventDefault();
        return;
      }
      if (navigationInteraction() && navigationKey) {
        wakeKey = key;
        event.preventDefault();
        return;
      }
    }
    var choices = (panel || nav).querySelectorAll('button');
    var at = Array.prototype.indexOf.call(choices, document.activeElement);
    if (key === 9 && panel) {
      var tab = at < 0 ? (event.shiftKey ? choices.length - 1 : 0)
        : (at + (event.shiftKey ? -1 : 1) + choices.length) % choices.length;
      choices[tab].focus();
      event.preventDefault();
    } else if (key === 37 || key === 38 || key === 39 || key === 40) {
      var next = Math.max(0, Math.min(choices.length - 1, at + (key === 37 || key === 38 ? -1 : 1)));
      var target = panel && settingsTarget(key);
      (target || choices[next]).focus();
      if (!panel) focus = next;
      event.preventDefault();
    } else if (key === 8 || key === 27 || key === 461 || key === 10009) {
      if (panel) closePanel();
      else TV.back();
      event.preventDefault();
    }
  }
  function start() {
    var stage = document.getElementById('stage');
    stage.innerHTML = '';
    document.body.className = '';
    document.body.style.background = '#f3efe8';
    surface = node('div', 'art-surface', stage);
    surface.setAttribute('aria-label', 'Art TV');
    var mount = node('div', 'art-mount', surface);
    picture = node('div', 'art-picture', mount);
    picture.setAttribute('role', 'img');
    picture.setAttribute('aria-label', tr('Background image'));
    var blur = node('div', 'art-menu-blur', surface);
    blur.setAttribute('aria-hidden', 'true');
    blurPicture = node('div', 'art-picture', node('div', 'art-mount', blur));
    var veil = node('div', 'art-menu-veil', surface);
    veil.setAttribute('aria-hidden', 'true');
    nav = node('div', 'art-nav', surface);
    ['Das Erste', 'ZDF', 'KiKA', tr('Live TV'), 'Apple TV', tr('Settings')].forEach(function (label, i) {
      var item = button(nav, '', function () {
        focus = i;
        if (i < 3) notice(label + ': ' + tr('Unavailable'));
        else if (i === 3) liveTV();
        else if (i === 4) inputs(true);
        else settings();
      });
      item.setAttribute('aria-label', label + (i === 4 ? ', HDMI' : ''));
      item.onfocus = function () { focus = i; };
      node('span', 'art-label', item, label);
    });
    background();
    fit();
    hideNavigation();
    surface.addEventListener('mousedown', pointerStart, true);
    surface.addEventListener('touchstart', pointerStart, true);
    surface.addEventListener('touchmove', navigationInteraction);
    surface.addEventListener('click', pointerClick, true);
    surface.addEventListener('mousemove', pointerMove);
    surface.addEventListener('wheel', function (event) {
      if (navigationInteraction()) event.preventDefault();
    });
    document.addEventListener('keydown', keys);
    document.addEventListener('keyup', function (event) {
      if (wakeKey && wakeKey === event.keyCode) {
        wakeKey = 0;
        event.preventDefault();
      }
    });
    document.addEventListener('visibilitychange', function () {
      if (document.hidden) {
        hideNavigation();
        wakeKey = 0;
        wakePointer = false;
      }
    });
    window.addEventListener('blur', hideNavigation);
    window.addEventListener('resize', fit);
    return true;
  }
  return {start: start};
})();
