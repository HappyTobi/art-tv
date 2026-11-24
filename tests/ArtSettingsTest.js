'use strict';
var assert = require('assert'), fs = require('fs'), path = require('path'), vm = require('vm');
var source = fs.readFileSync(path.join(__dirname, '..', 'preview', 'art.js'), 'utf8');

function preview() {
  var document = {activeElement: null}, listeners = {}, stored = {'arttv.artBackground': 'art-4'}, launches = [];
  var now = 0, timerId = 0, timers = {}, windowListeners = {}, pointerSequence = 0, images = [];
  function Element(tag) {
    this.tag = tag;
    this.children = [];
    this.style = {};
    this.attributes = {};
    this.className = '';
    this.listeners = {};
  }
  Element.prototype.appendChild = function (child) {
    child.parentNode = this;
    this.children.push(child);
  };
  Element.prototype.removeChild = function (child) {
    this.children.splice(this.children.indexOf(child), 1);
    child.parentNode = null;
  };
  Element.prototype.setAttribute = function (key, value) { this.attributes[key] = value; };
  Element.prototype.focus = function () {
    document.activeElement = this;
    if (this.onfocus) this.onfocus();
  };
  Element.prototype.addEventListener = function (name, callback) { this.listeners[name] = callback; };
  function matches(element, selector) {
    var tag = selector.match(/^[a-z]+/), cls = selector.match(/\.([\w-]+)/), attr = selector.match(/\[([\w-]+)="([^"]*)"\]/);
    return (!tag || element.tag === tag[0]) &&
      (!cls || (' ' + element.className + ' ').indexOf(' ' + cls[1] + ' ') >= 0) &&
      (!attr || element.attributes[attr[1]] === attr[2]);
  }
  Element.prototype.querySelectorAll = function (selector) {
    var parts = selector.split(' '), found = [], root = this;
    function chain(element, at) {
      if (!matches(element, parts[at])) return false;
      if (!at) return true;
      for (var parent = element.parentNode; parent && parent !== root.parentNode; parent = parent.parentNode)
        if (chain(parent, at - 1)) return true;
      return false;
    }
    function visit(element) {
      element.children.forEach(function (child) {
        if (chain(child, parts.length - 1)) found.push(child);
        visit(child);
      });
    }
    visit(this);
    return found;
  };
  Element.prototype.querySelector = function (selector) { return this.querySelectorAll(selector)[0] || null; };
  Object.defineProperty(Element.prototype, 'innerHTML', {set: function () { this.children = []; }});
  Object.defineProperty(Element.prototype, 'firstChild', {get: function () { return this.children[0] || null; }});
  var stage = new Element('div');
  document.body = new Element('body');
  document.body.appendChild(stage);
  document.createElement = function (tag) { return new Element(tag); };
  document.getElementById = function () { return stage; };
  document.addEventListener = function (name, callback) { listeners[name] = callback; };
  var sandbox = {
    document: document, innerWidth: 1920, innerHeight: 1080,
    addEventListener: function (name, callback) { windowListeners[name] = callback; },
    Image: function () { var image = new Element('img'); images.push(image); return image; },
    setTimeout: function (callback, delay) {
      timers[++timerId] = {at: now + delay, callback: callback};
      return timerId;
    },
    clearTimeout: function (id) { delete timers[id]; },
    localStorage: {
      getItem: function (key) { return stored[key] || null; },
      setItem: function (key, value) { stored[key] = value; }
    },
    tr: function (text) { return text; },
    TV: {
      name: 'browser',
      inputs: function (callback) {
        callback([{id: 'hdmi-1', key: 'hdmi1', label: 'HDMI 1'}, {id: 'hdmi-2', key: 'hdmi2', label: 'HDMI 2'}]);
      },
      launch: function (id, unused, callback) { launches.push(id); callback(true); }
    },
    ARTWORKS: Array.from({length: 6}, function (_, i) { return {id: 'art-' + i, title: 'Artwork ' + i, file: i + '.jpg'}; })
  };
  sandbox.window = sandbox;
  vm.runInNewContext(source, sandbox, {filename: 'web/art.js'});
  sandbox.ArtTV.start();
  function key(code, shift, up) {
    var event = {keyCode: code, shiftKey: !!shift, preventDefault: function () { this.prevented = true; }};
    listeners[up ? 'keyup' : 'keydown'](event);
    return event;
  }
  return {
    sandbox: sandbox, document: document, stage: stage, stored: stored, launches: launches, images: images,
    key: key,
    advance: function (milliseconds) {
      var until = now + milliseconds;
      while (true) {
        var due = Object.keys(timers).sort(function (a, b) { return timers[a].at - timers[b].at; })[0];
        if (!due || timers[due].at > until) break;
        var timer = timers[due];
        now = timer.at;
        delete timers[due];
        timer.callback();
      }
      now = until;
    },
    pointer: function (type, x, y) {
      var event = {
        clientX: x === undefined ? ++pointerSequence : x, clientY: y === undefined ? 0 : y,
        preventDefault: function () { this.prevented = true; }, stopPropagation: function () { this.stopped = true; }
      };
      stage.querySelector('.art-surface').listeners[type](event);
      return event;
    },
    blur: function () { windowListeners.blur(); },
    visibility: function (hidden) { document.hidden = hidden; listeners.visibilitychange(); },
    settings: function () {
      if (stage.querySelector('.art-nav').attributes['aria-hidden'] === 'true') {
        key(13);
        key(13, false, true);
      }
      var item = stage.querySelectorAll('.art-nav button')[5];
      item.focus();
      item.onclick();
    }
  };
}

var p = preview();
p.images[0].onload();
var originalPicture = p.stage.querySelector('.art-mount .art-picture');
var blurPicture = p.stage.querySelector('.art-menu-blur .art-picture');
assert.strictEqual(blurPicture.style.backgroundImage, originalPicture.style.backgroundImage,
  'The bottom blur uses the same loaded artwork as the sharp original');
p.settings();
p.stage.querySelectorAll('.art-gallery button')[0].onclick();
p.settings();
p.stage.querySelectorAll('.art-gallery button')[1].onclick();
p.images[2].onload();
p.images[1].onload();
assert.strictEqual(originalPicture.style.backgroundImage, 'url("art/1.jpg")');
assert.strictEqual(blurPicture.style.backgroundImage, originalPicture.style.backgroundImage,
  'A late artwork load cannot leave the blur showing an older picture');
assert.strictEqual(originalPicture.children.length, 1);
p = preview();
p.settings();
var cards = p.stage.querySelectorAll('.art-gallery button'), sidebar = p.stage.querySelectorAll('.art-sidebar button');
assert.strictEqual(sidebar.length, 4, 'Only artwork, HDMI, Live TV and Sony home shortcuts remain');
assert.strictEqual(p.document.activeElement, sidebar[0]);
p.key(39);
assert.strictEqual(p.document.activeElement, cards[4], 'Right enters the gallery at the current artwork');
p.key(38);
assert.strictEqual(p.document.activeElement, cards[1], 'Up keeps the gallery column');
p.key(37);
p.key(37);
assert.strictEqual(p.document.activeElement, sidebar[0], 'Left from the first column returns to the sidebar');
p.key(40);
assert.strictEqual(p.document.activeElement, sidebar[1], 'Down moves through sidebar actions');
p.key(39);
p.key(40);
assert.strictEqual(p.document.activeElement, cards[4], 'Down at the last row stays in the gallery');
p.key(38);
p.key(38);
assert.strictEqual(p.document.activeElement, p.stage.querySelector('.art-close'), 'Up from the first row reaches Cancel');
p.key(9, true);
assert.strictEqual(p.document.activeElement, cards[5], 'Shift+Tab wraps inside the settings dialog');
p.key(9);
assert.strictEqual(p.document.activeElement, p.stage.querySelector('.art-close'), 'Tab cannot focus the home menu behind settings');
cards[2].onclick();
assert.strictEqual(p.stored['arttv.artBackground'], 'art-2', 'Choosing artwork persists its stable id');
assert.strictEqual(p.stage.querySelector('.art-panel'), null);
assert.strictEqual(p.document.activeElement, p.stage.querySelectorAll('.art-nav button')[5]);

p = preview();
p.stored['arttv.artInput'] = 'hdmi-2';
p.settings();
p.stage.querySelectorAll('.art-sidebar button')[1].onclick();
var inputs = p.stage.querySelectorAll('.art-input-list button');
assert.strictEqual(p.document.activeElement, inputs[1], 'The HDMI chooser focuses the saved port');
inputs[0].onclick();
assert.strictEqual(p.stored['arttv.artInput'], 'hdmi-1');
assert.strictEqual(p.launches.length, 0, 'Configuring a port does not switch the TV input');
p.stage.querySelectorAll('.art-nav button')[4].onclick();
assert.deepStrictEqual(p.launches, ['hdmi-1'], 'Apple TV still launches the configured port');

p = preview();
p.sandbox.TV.inputs = function (callback) { callback([]); };
p.settings();
p.stage.querySelectorAll('.art-sidebar button')[1].onclick();
assert.strictEqual(p.document.activeElement, p.stage.querySelector('.art-close'), 'An empty HDMI chooser still has a focused way back');
p.key(27);
assert.strictEqual(p.stage.querySelector('.art-panel'), null);

p = preview();
p.settings();
var complete;
p.sandbox.TV.inputs = function (callback) { complete = callback; };
p.stage.querySelectorAll('.art-sidebar button')[1].onclick();
p.key(27);
complete([{id: 'hdmi-1', key: 'hdmi1'}]);
assert.strictEqual(p.stage.querySelector('.art-panel'), null, 'A cancelled input lookup cannot reopen settings');

p = preview();
p.sandbox.ARTWORKS.pop();
p.settings();
cards = p.stage.querySelectorAll('.art-gallery button');
cards[2].focus();
p.key(40);
assert.strictEqual(p.document.activeElement, cards[2], 'A partial last row never focuses a missing card');

p = preview();
var nav = p.stage.querySelector('.art-nav'), menu = nav.querySelectorAll('button');
var surface = p.stage.querySelector('.art-surface');
assert.strictEqual(nav.attributes['aria-hidden'], 'true', 'The artwork starts without navigation');
assert.strictEqual(surface.className, 'art-surface', 'The resting artwork is sharp');
assert.ok(menu.every(function (item) { return item.disabled && item.tabIndex === -1; }));
assert.ok(p.key(13).prevented, 'The first OK only wakes navigation');
assert.strictEqual(nav.attributes['aria-hidden'], 'false');
assert.strictEqual(surface.className, 'art-surface art-menu-visible', 'Wake-up also blurs the artwork and frame');
assert.ok(p.key(13).prevented, 'A held wake-up key cannot activate a shortcut');
assert.ok(p.key(13, false, true).prevented, 'The wake-up key release is also consumed');
p.advance(19999);
assert.strictEqual(nav.attributes['aria-hidden'], 'false', 'Navigation remains visible before 20 seconds');
p.advance(1);
assert.strictEqual(nav.attributes['aria-hidden'], 'true', 'Navigation hides at exactly 20 seconds');
assert.strictEqual(surface.className, 'art-surface', 'The original sharp artwork returns at the same timeout');
assert.strictEqual(p.stage.querySelector('.art-panel'), null, 'Waking never opens a settings panel');
assert.strictEqual(p.launches.length, 0, 'Waking never launches an input or app');

p.key(39);
assert.strictEqual(p.document.activeElement, menu[0], 'The first arrow reveals without moving selection');
p.key(39, false, true);
p.key(39);
assert.strictEqual(p.document.activeElement, menu[1]);
p.advance(15000);
p.key(39);
p.advance(19999);
assert.strictEqual(nav.attributes['aria-hidden'], 'false', 'Activity restarts the full idle timeout');
p.advance(1);
assert.strictEqual(nav.attributes['aria-hidden'], 'true');
p.key(13);
assert.strictEqual(p.document.activeElement, menu[2], 'Wake-up restores the last selected shortcut');
p.key(13, false, true);

p.settings();
p.advance(60000);
assert.ok(p.stage.querySelector('.art-panel'), 'Settings never disappear because of main-screen inactivity');
p.key(27);
p.advance(19999);
assert.strictEqual(nav.attributes['aria-hidden'], 'false', 'Closing settings starts a fresh timeout');
p.advance(1);
assert.strictEqual(nav.attributes['aria-hidden'], 'true');
assert.ok(p.pointer('mousedown').prevented, 'The first pointer press only reveals navigation');
assert.ok(p.pointer('click').stopped, 'The rest of the wake-up click cannot reach a shortcut');
assert.ok(!p.pointer('mousedown').prevented, 'A subsequent click is usable');
assert.ok(!p.pointer('click').stopped);
p.advance(20000);
assert.ok(p.pointer('mousedown').prevented);
p.key(39);
assert.ok(!p.pointer('click').stopped, 'Switching to the remote clears a suppressed pointer gesture');
p.advance(20000);
assert.ok(p.pointer('touchstart').prevented, 'Touch has the same safe wake-up gesture');
assert.ok(p.pointer('click').stopped);
p.advance(20000);
p.pointer('mousemove');
assert.strictEqual(nav.attributes['aria-hidden'], 'false', 'Mouse motion reveals navigation');
p.pointer('mousemove', 200, 300);
p.advance(15000);
p.pointer('mousemove', 200, 300);
p.advance(5000);
assert.strictEqual(nav.attributes['aria-hidden'], 'true', 'A stationary pointer cannot keep the menu awake');
p.pointer('mousemove', 200, 300);
assert.strictEqual(nav.attributes['aria-hidden'], 'true', 'Stationary hover cannot reveal the hidden menu');
p.pointer('mousemove', 201, 300);
assert.strictEqual(nav.attributes['aria-hidden'], 'false');
p.blur();
assert.strictEqual(nav.attributes['aria-hidden'], 'true', 'Leaving the window suspends navigation');
p.pointer('mousemove');
p.visibility(true);
assert.strictEqual(nav.attributes['aria-hidden'], 'true', 'Leaving the app hides navigation');
console.log('ArtSettingsTest: all checks passed.');
