/* Offline stand-ins: the design preview never connects to or controls a TV. */
function tr(text) { return text; }
var TV = {
  name: 'browser',
  inputs: function (done) {
    done([
      {id: 'hdmi-1', key: 'hdmi1', label: 'HDMI 1'},
      {id: 'hdmi-2', key: 'hdmi2', label: 'HDMI 2'},
      {id: 'hdmi-3', key: 'hdmi3', label: 'HDMI 3'},
      {id: 'hdmi-4', key: 'hdmi4', label: 'HDMI 4'}
    ]);
  },
  launch: function (id, unused, done) { done(false); },
  back: function () {}
};
