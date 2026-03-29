var xCloudPlayer = (() => {
  var __create = Object.create;
  var __defProp = Object.defineProperty;
  var __getOwnPropDesc = Object.getOwnPropertyDescriptor;
  var __getOwnPropNames = Object.getOwnPropertyNames;
  var __getProtoOf = Object.getPrototypeOf;
  var __hasOwnProp = Object.prototype.hasOwnProperty;
  var __commonJS = (cb, mod) => function __require() {
    return mod || (0, cb[__getOwnPropNames(cb)[0]])((mod = { exports: {} }).exports, mod), mod.exports;
  };
  var __export = (target, all) => {
    for (var name in all)
      __defProp(target, name, { get: all[name], enumerable: true });
  };
  var __copyProps = (to, from, except, desc) => {
    if (from && typeof from === "object" || typeof from === "function") {
      for (let key of __getOwnPropNames(from))
        if (!__hasOwnProp.call(to, key) && key !== except)
          __defProp(to, key, { get: () => from[key], enumerable: !(desc = __getOwnPropDesc(from, key)) || desc.enumerable });
    }
    return to;
  };
  var __toESM = (mod, isNodeMode, target) => (target = mod != null ? __create(__getProtoOf(mod)) : {}, __copyProps(
    // If the importer is in node compatibility mode or this is not an ESM
    // file that has been converted to a CommonJS file using a Babel-
    // compatible transform (i.e. "__esModule" has not been set), then set
    // "default" to the CommonJS "module.exports" for node compatibility.
    isNodeMode || !mod || !mod.__esModule ? __defProp(target, "default", { value: mod, enumerable: true }) : target,
    mod
  ));
  var __toCommonJS = (mod) => __copyProps(__defProp({}, "__esModule", { value: true }), mod);

  // node_modules/events/events.js
  var require_events = __commonJS({
    "node_modules/events/events.js"(exports, module) {
      "use strict";
      var R = typeof Reflect === "object" ? Reflect : null;
      var ReflectApply = R && typeof R.apply === "function" ? R.apply : function ReflectApply2(target, receiver, args) {
        return Function.prototype.apply.call(target, receiver, args);
      };
      var ReflectOwnKeys;
      if (R && typeof R.ownKeys === "function") {
        ReflectOwnKeys = R.ownKeys;
      } else if (Object.getOwnPropertySymbols) {
        ReflectOwnKeys = function ReflectOwnKeys2(target) {
          return Object.getOwnPropertyNames(target).concat(Object.getOwnPropertySymbols(target));
        };
      } else {
        ReflectOwnKeys = function ReflectOwnKeys2(target) {
          return Object.getOwnPropertyNames(target);
        };
      }
      function ProcessEmitWarning(warning) {
        if (console && console.warn) console.warn(warning);
      }
      var NumberIsNaN = Number.isNaN || function NumberIsNaN2(value) {
        return value !== value;
      };
      function EventEmitter2() {
        EventEmitter2.init.call(this);
      }
      module.exports = EventEmitter2;
      module.exports.once = once;
      EventEmitter2.EventEmitter = EventEmitter2;
      EventEmitter2.prototype._events = void 0;
      EventEmitter2.prototype._eventsCount = 0;
      EventEmitter2.prototype._maxListeners = void 0;
      var defaultMaxListeners = 10;
      function checkListener(listener) {
        if (typeof listener !== "function") {
          throw new TypeError('The "listener" argument must be of type Function. Received type ' + typeof listener);
        }
      }
      Object.defineProperty(EventEmitter2, "defaultMaxListeners", {
        enumerable: true,
        get: function() {
          return defaultMaxListeners;
        },
        set: function(arg) {
          if (typeof arg !== "number" || arg < 0 || NumberIsNaN(arg)) {
            throw new RangeError('The value of "defaultMaxListeners" is out of range. It must be a non-negative number. Received ' + arg + ".");
          }
          defaultMaxListeners = arg;
        }
      });
      EventEmitter2.init = function() {
        if (this._events === void 0 || this._events === Object.getPrototypeOf(this)._events) {
          this._events = /* @__PURE__ */ Object.create(null);
          this._eventsCount = 0;
        }
        this._maxListeners = this._maxListeners || void 0;
      };
      EventEmitter2.prototype.setMaxListeners = function setMaxListeners(n) {
        if (typeof n !== "number" || n < 0 || NumberIsNaN(n)) {
          throw new RangeError('The value of "n" is out of range. It must be a non-negative number. Received ' + n + ".");
        }
        this._maxListeners = n;
        return this;
      };
      function _getMaxListeners(that) {
        if (that._maxListeners === void 0)
          return EventEmitter2.defaultMaxListeners;
        return that._maxListeners;
      }
      EventEmitter2.prototype.getMaxListeners = function getMaxListeners() {
        return _getMaxListeners(this);
      };
      EventEmitter2.prototype.emit = function emit(type) {
        var args = [];
        for (var i = 1; i < arguments.length; i++) args.push(arguments[i]);
        var doError = type === "error";
        var events = this._events;
        if (events !== void 0)
          doError = doError && events.error === void 0;
        else if (!doError)
          return false;
        if (doError) {
          var er;
          if (args.length > 0)
            er = args[0];
          if (er instanceof Error) {
            throw er;
          }
          var err = new Error("Unhandled error." + (er ? " (" + er.message + ")" : ""));
          err.context = er;
          throw err;
        }
        var handler = events[type];
        if (handler === void 0)
          return false;
        if (typeof handler === "function") {
          ReflectApply(handler, this, args);
        } else {
          var len = handler.length;
          var listeners = arrayClone(handler, len);
          for (var i = 0; i < len; ++i)
            ReflectApply(listeners[i], this, args);
        }
        return true;
      };
      function _addListener(target, type, listener, prepend) {
        var m;
        var events;
        var existing;
        checkListener(listener);
        events = target._events;
        if (events === void 0) {
          events = target._events = /* @__PURE__ */ Object.create(null);
          target._eventsCount = 0;
        } else {
          if (events.newListener !== void 0) {
            target.emit(
              "newListener",
              type,
              listener.listener ? listener.listener : listener
            );
            events = target._events;
          }
          existing = events[type];
        }
        if (existing === void 0) {
          existing = events[type] = listener;
          ++target._eventsCount;
        } else {
          if (typeof existing === "function") {
            existing = events[type] = prepend ? [listener, existing] : [existing, listener];
          } else if (prepend) {
            existing.unshift(listener);
          } else {
            existing.push(listener);
          }
          m = _getMaxListeners(target);
          if (m > 0 && existing.length > m && !existing.warned) {
            existing.warned = true;
            var w = new Error("Possible EventEmitter memory leak detected. " + existing.length + " " + String(type) + " listeners added. Use emitter.setMaxListeners() to increase limit");
            w.name = "MaxListenersExceededWarning";
            w.emitter = target;
            w.type = type;
            w.count = existing.length;
            ProcessEmitWarning(w);
          }
        }
        return target;
      }
      EventEmitter2.prototype.addListener = function addListener(type, listener) {
        return _addListener(this, type, listener, false);
      };
      EventEmitter2.prototype.on = EventEmitter2.prototype.addListener;
      EventEmitter2.prototype.prependListener = function prependListener(type, listener) {
        return _addListener(this, type, listener, true);
      };
      function onceWrapper() {
        if (!this.fired) {
          this.target.removeListener(this.type, this.wrapFn);
          this.fired = true;
          if (arguments.length === 0)
            return this.listener.call(this.target);
          return this.listener.apply(this.target, arguments);
        }
      }
      function _onceWrap(target, type, listener) {
        var state = { fired: false, wrapFn: void 0, target, type, listener };
        var wrapped = onceWrapper.bind(state);
        wrapped.listener = listener;
        state.wrapFn = wrapped;
        return wrapped;
      }
      EventEmitter2.prototype.once = function once2(type, listener) {
        checkListener(listener);
        this.on(type, _onceWrap(this, type, listener));
        return this;
      };
      EventEmitter2.prototype.prependOnceListener = function prependOnceListener(type, listener) {
        checkListener(listener);
        this.prependListener(type, _onceWrap(this, type, listener));
        return this;
      };
      EventEmitter2.prototype.removeListener = function removeListener(type, listener) {
        var list, events, position, i, originalListener;
        checkListener(listener);
        events = this._events;
        if (events === void 0)
          return this;
        list = events[type];
        if (list === void 0)
          return this;
        if (list === listener || list.listener === listener) {
          if (--this._eventsCount === 0)
            this._events = /* @__PURE__ */ Object.create(null);
          else {
            delete events[type];
            if (events.removeListener)
              this.emit("removeListener", type, list.listener || listener);
          }
        } else if (typeof list !== "function") {
          position = -1;
          for (i = list.length - 1; i >= 0; i--) {
            if (list[i] === listener || list[i].listener === listener) {
              originalListener = list[i].listener;
              position = i;
              break;
            }
          }
          if (position < 0)
            return this;
          if (position === 0)
            list.shift();
          else {
            spliceOne(list, position);
          }
          if (list.length === 1)
            events[type] = list[0];
          if (events.removeListener !== void 0)
            this.emit("removeListener", type, originalListener || listener);
        }
        return this;
      };
      EventEmitter2.prototype.off = EventEmitter2.prototype.removeListener;
      EventEmitter2.prototype.removeAllListeners = function removeAllListeners(type) {
        var listeners, events, i;
        events = this._events;
        if (events === void 0)
          return this;
        if (events.removeListener === void 0) {
          if (arguments.length === 0) {
            this._events = /* @__PURE__ */ Object.create(null);
            this._eventsCount = 0;
          } else if (events[type] !== void 0) {
            if (--this._eventsCount === 0)
              this._events = /* @__PURE__ */ Object.create(null);
            else
              delete events[type];
          }
          return this;
        }
        if (arguments.length === 0) {
          var keys = Object.keys(events);
          var key;
          for (i = 0; i < keys.length; ++i) {
            key = keys[i];
            if (key === "removeListener") continue;
            this.removeAllListeners(key);
          }
          this.removeAllListeners("removeListener");
          this._events = /* @__PURE__ */ Object.create(null);
          this._eventsCount = 0;
          return this;
        }
        listeners = events[type];
        if (typeof listeners === "function") {
          this.removeListener(type, listeners);
        } else if (listeners !== void 0) {
          for (i = listeners.length - 1; i >= 0; i--) {
            this.removeListener(type, listeners[i]);
          }
        }
        return this;
      };
      function _listeners(target, type, unwrap) {
        var events = target._events;
        if (events === void 0)
          return [];
        var evlistener = events[type];
        if (evlistener === void 0)
          return [];
        if (typeof evlistener === "function")
          return unwrap ? [evlistener.listener || evlistener] : [evlistener];
        return unwrap ? unwrapListeners(evlistener) : arrayClone(evlistener, evlistener.length);
      }
      EventEmitter2.prototype.listeners = function listeners(type) {
        return _listeners(this, type, true);
      };
      EventEmitter2.prototype.rawListeners = function rawListeners(type) {
        return _listeners(this, type, false);
      };
      EventEmitter2.listenerCount = function(emitter, type) {
        if (typeof emitter.listenerCount === "function") {
          return emitter.listenerCount(type);
        } else {
          return listenerCount.call(emitter, type);
        }
      };
      EventEmitter2.prototype.listenerCount = listenerCount;
      function listenerCount(type) {
        var events = this._events;
        if (events !== void 0) {
          var evlistener = events[type];
          if (typeof evlistener === "function") {
            return 1;
          } else if (evlistener !== void 0) {
            return evlistener.length;
          }
        }
        return 0;
      }
      EventEmitter2.prototype.eventNames = function eventNames() {
        return this._eventsCount > 0 ? ReflectOwnKeys(this._events) : [];
      };
      function arrayClone(arr, n) {
        var copy = new Array(n);
        for (var i = 0; i < n; ++i)
          copy[i] = arr[i];
        return copy;
      }
      function spliceOne(list, index) {
        for (; index + 1 < list.length; index++)
          list[index] = list[index + 1];
        list.pop();
      }
      function unwrapListeners(arr) {
        var ret = new Array(arr.length);
        for (var i = 0; i < ret.length; ++i) {
          ret[i] = arr[i].listener || arr[i];
        }
        return ret;
      }
      function once(emitter, name) {
        return new Promise(function(resolve, reject) {
          function errorListener(err) {
            emitter.removeListener(name, resolver);
            reject(err);
          }
          function resolver() {
            if (typeof emitter.removeListener === "function") {
              emitter.removeListener("error", errorListener);
            }
            resolve([].slice.call(arguments));
          }
          ;
          eventTargetAgnosticAddListener(emitter, name, resolver, { once: true });
          if (name !== "error") {
            addErrorHandlerIfEventEmitter(emitter, errorListener, { once: true });
          }
        });
      }
      function addErrorHandlerIfEventEmitter(emitter, handler, flags) {
        if (typeof emitter.on === "function") {
          eventTargetAgnosticAddListener(emitter, "error", handler, flags);
        }
      }
      function eventTargetAgnosticAddListener(emitter, name, listener, flags) {
        if (typeof emitter.on === "function") {
          if (flags.once) {
            emitter.once(name, listener);
          } else {
            emitter.on(name, listener);
          }
        } else if (typeof emitter.addEventListener === "function") {
          emitter.addEventListener(name, function wrapListener(arg) {
            if (flags.once) {
              emitter.removeEventListener(name, wrapListener);
            }
            listener(arg);
          });
        } else {
          throw new TypeError('The "emitter" argument must be of type EventEmitter. Received type ' + typeof emitter);
        }
      }
    }
  });

  // src/Library.ts
  var Library_exports = {};
  __export(Library_exports, {
    default: () => xCloudPlayer,
    xCloudPlayerBackend: () => xCloudPlayerBackend
  });

  // src/Helper/FpsCounter.ts
  var FpsCounter = class {
    _name;
    _application;
    _counter = 0;
    _eventInterval;
    constructor(application, name) {
      this._name = name;
      this._application = application;
    }
    start() {
      this._eventInterval = setInterval(() => {
        this._application.getEventBus().emit("fps_" + this._name, {
          fps: this._counter
        });
        this._counter = 0;
      }, 1e3);
    }
    stop() {
      clearInterval(this._eventInterval);
    }
    count() {
      this._counter++;
    }
  };

  // src/Channel/Base.ts
  var BaseChannel = class {
    _client;
    _channelName;
    _state;
    _events = {
      "state": []
    };
    constructor(channelName, client) {
      this._channelName = channelName;
      this._client = client;
      this._state = "new";
    }
    // Events
    onOpen(event) {
      console.log("xCloudPlayer Channels/Base.ts - [" + this._channelName + "] onOpen:", event);
      this.setState("connected");
    }
    // onMessage(event) {
    //     console.log('xSDK channel/base.js - ['+this._channelName+'] onMessage:', event)
    // }
    onClosing(event) {
      console.log("xCloudPlayer Channel/Base.ts - [" + this._channelName + "] onClosing:", event);
      this.setState("closing");
    }
    onClose(event) {
      console.log("xCloudPlayer Channel/Base.ts - [" + this._channelName + "] onClose:", event);
      this.setState("closed");
    }
    destroy() {
    }
    setState(state) {
      this._state = state;
      this.emitEvent("state", {
        state: this._state
      });
    }
    // Channel functions
    send(data) {
      const channel = this.getClient().getChannel(this._channelName);
      if (channel.readyState === "open") {
        if (this._channelName !== "input" && this._channelName !== "chat") {
          console.log("xCloudPlayer Channel/Base.ts - [" + this._channelName + "] Sending message:", data);
        }
        if (typeof data === "string") {
          data = new TextEncoder().encode(data);
        }
        channel.send(data);
      } else {
        console.warn("xCloudPlayer Channel/Base.ts - [" + this._channelName + "] Channel is closed. Failed to send packet:", data);
      }
    }
    // Base functions
    getClient() {
      return this._client;
    }
    addEventListener(name, callback) {
      this._events[name].push(callback);
    }
    emitEvent(name, event) {
      for (const callback in this._events[name]) {
        this._events[name][callback](event);
      }
    }
  };

  // src/Channel/Input/Packet.ts
  var InputPacket = class {
    _reportType = 0 /* None */;
    _totalSize = -1;
    _sequence = -1;
    _metadataFrames = [];
    _gamepadFrames = [];
    _pointerFrames = [];
    _mouseFrames = [];
    _keyboardFrames = [];
    _maxTouchpoints = 0;
    constructor(sequence) {
      this._sequence = sequence;
    }
    setMetadata(maxTouchpoints = 1) {
      this._reportType = 8 /* ClientMetadata */;
      this._totalSize = 15;
      this._maxTouchpoints = maxTouchpoints;
    }
    setData(metadataQueue, gamepadQueue, pointerQueue, mouseQueue, keyboardQueue) {
      let size = 14;
      if (metadataQueue.length > 0) {
        this._reportType |= 1 /* Metadata */;
        size = size + this._calculateMetadataSize(metadataQueue);
        this._metadataFrames = metadataQueue;
      }
      if (gamepadQueue.length > 0) {
        this._reportType |= 2 /* Gamepad */;
        size = size + this._calculateGamepadSize(gamepadQueue);
        this._gamepadFrames = gamepadQueue;
      }
      if (pointerQueue.length > 0) {
        this._reportType |= 4 /* Pointer */;
        size = size + this._calculatePointerSize(pointerQueue);
        this._pointerFrames = pointerQueue;
      }
      if (mouseQueue.length > 0) {
        this._reportType |= 32 /* Mouse */;
        size = size + this._calculateMouseSize(mouseQueue);
        this._mouseFrames = mouseQueue;
      }
      if (keyboardQueue.length > 0) {
        this._reportType |= 64 /* Keyboard */;
        size = size + this._calculateKeyboardSize(keyboardQueue);
        this._keyboardFrames = keyboardQueue;
      }
      this._totalSize = size;
    }
    _calculateMetadataSize(frames) {
      return 1 + 7 * 4 * frames.length;
    }
    _calculateGamepadSize(frames) {
      return 1 + 23 * frames.length;
    }
    _calculatePointerSize(frames) {
      let pointerSize = 1;
      for (const frame in frames) {
        pointerSize = pointerSize + 1 + frames[frame].events.length * 20;
      }
      return pointerSize;
    }
    _calculateMouseSize(frames) {
      return 1 + 18 * frames.length;
    }
    _calculateKeyboardSize(frames) {
      return 1 + 3 * frames.length;
    }
    _writeMetadataData(packet, offset, frames) {
      packet.setUint8(offset, frames.length);
      offset++;
      if (frames.length >= 30) {
        console.warn("metadataQueue is bigger then 30. This might impact reliability!");
      }
      for (; frames.length > 0; ) {
        const frame = frames.shift();
        const firstFramePacketArrivalTimeMs = frame.firstFramePacketArrivalTimeMs;
        const frameSubmittedTimeMs = frame.frameSubmittedTimeMs;
        const frameDecodedTimeMs = frame.frameDecodedTimeMs;
        const frameRenderedTimeMs = frame.frameRenderedTimeMs;
        const framePacketTime = performance.now();
        const frameDateNow = performance.now();
        packet.setUint32(offset, frame.serverDataKey, true);
        packet.setUint32(offset + 4, firstFramePacketArrivalTimeMs, true);
        packet.setUint32(offset + 8, frameSubmittedTimeMs, true);
        packet.setUint32(offset + 12, frameDecodedTimeMs, true);
        packet.setUint32(offset + 16, frameRenderedTimeMs, true);
        packet.setUint32(offset + 20, framePacketTime, true);
        packet.setUint32(offset + 24, frameDateNow, true);
        offset += 28;
      }
      return offset;
    }
    _writeGamepadData(packet, offset, frames) {
      packet.setUint8(offset, frames.length);
      offset++;
      if (frames.length >= 30) {
        console.warn("gamepadQueue is bigger then 30. This might impact reliability!");
      }
      for (; frames.length > 0; ) {
        const shift = frames.shift();
        if (shift !== void 0) {
          const input = shift;
          packet.setUint8(offset, input.GamepadIndex);
          offset++;
          let buttonMask = 0;
          if (input.Nexus > 0) {
            buttonMask |= 2;
          }
          if (input.Menu > 0) {
            buttonMask |= 4;
          }
          if (input.View > 0) {
            buttonMask |= 8;
          }
          if (input.A > 0) {
            buttonMask |= 16;
          }
          if (input.B > 0) {
            buttonMask |= 32;
          }
          if (input.X > 0) {
            buttonMask |= 64;
          }
          if (input.Y > 0) {
            buttonMask |= 128;
          }
          if (input.DPadUp > 0) {
            buttonMask |= 256;
          }
          if (input.DPadDown > 0) {
            buttonMask |= 512;
          }
          if (input.DPadLeft > 0) {
            buttonMask |= 1024;
          }
          if (input.DPadRight > 0) {
            buttonMask |= 2048;
          }
          if (input.LeftShoulder > 0) {
            buttonMask |= 4096;
          }
          if (input.RightShoulder > 0) {
            buttonMask |= 8192;
          }
          if (input.LeftThumb > 0) {
            buttonMask |= 16384;
          }
          if (input.RightThumb > 0) {
            buttonMask |= 32768;
          }
          packet.setUint16(offset, buttonMask, true);
          packet.setInt16(offset + 2, this._normalizeAxisValue(input.LeftThumbXAxis), true);
          packet.setInt16(offset + 4, this._normalizeAxisValue(-input.LeftThumbYAxis), true);
          packet.setInt16(offset + 6, this._normalizeAxisValue(input.RightThumbXAxis), true);
          packet.setInt16(offset + 8, this._normalizeAxisValue(-input.RightThumbYAxis), true);
          packet.setUint16(offset + 10, this._normalizeTriggerValue(input.LeftTrigger), true);
          packet.setUint16(offset + 12, this._normalizeTriggerValue(input.RightTrigger), true);
          packet.setUint32(offset + 14, 0, true);
          packet.setUint32(offset + 18, 0, false);
          offset += 22;
        }
      }
      return offset;
    }
    _writePointerData(packet, offset, frames) {
      packet.setUint8(offset, 1);
      offset++;
      if (frames.length >= 2) {
        console.warn("pointerQueue is bigger then 1. Only one event will be sent.");
      }
      const shift = frames.shift();
      if (shift !== void 0) {
        packet.setUint8(offset, shift.events.length);
        offset++;
        const screenWidth = 1920 * 2;
        const screenHeight = 1080 * 2;
        for (const event in shift.events) {
          const rect = shift.events[event].target.getBoundingClientRect();
          let e = 0.06575749909301447 * (screenHeight / 1), n = 0.06575749909301447 * (screenWidth / 1);
          e = 1, n = 1;
          if (shift.events[event].type === "pointerup") {
            e = 0;
            n = 0;
          }
          packet.setUint16(offset, e, true);
          packet.setUint16(offset + 2, n, true);
          packet.setUint8(offset + 4, 255 * shift.events[event].pressure);
          packet.setUint16(offset + 5, shift.events[event].twist, true);
          packet.setUint32(offset + 7, 0, true);
          let o = (shift.events[event].x - rect.left) * (screenWidth / rect.width), l = (shift.events[event].y - rect.top) * (screenHeight / rect.height);
          if (shift.events[event].type === "pointerup") {
            o = 0;
            l = 0;
          }
          packet.setUint32(offset + 11, o, true);
          packet.setUint32(offset + 15, l, true);
          packet.setUint8(offset + 19, shift.events[event].type === "pointerdown" ? 1 : shift.events[event].type === "pointerup" ? 2 : shift.events[event].type === "pointermove" ? 3 : 0);
          offset = offset + 20;
        }
      }
      return offset;
    }
    _writeMouseData(packet, offset, frames) {
      packet.setUint8(offset, frames.length);
      offset++;
      if (frames.length >= 30) {
        console.warn("mouseQueue is bigger then 30. This might impact reliability!");
      }
      for (; frames.length > 0; ) {
        const shift = frames.shift();
        if (shift !== void 0) {
          const input = shift;
          packet.setUint32(offset, input.X, true);
          packet.setUint32(offset + 4, input.Y, true);
          packet.setUint32(offset + 8, input.WheelX, true);
          packet.setUint32(offset + 12, input.WheelY, true);
          packet.setUint8(offset + 16, input.Buttons);
          packet.setUint8(offset + 17, input.Relative);
          offset += 18;
        }
      }
      return offset;
    }
    _writeKeyboardData(packet, offset, frames) {
      packet.setUint8(offset, frames.length);
      offset++;
      if (frames.length >= 30) {
        console.warn("keyboardQueue is bigger then 30. This might impact reliability!");
      }
      for (; frames.length > 0; ) {
        const shift = frames.shift();
        if (shift !== void 0) {
          const input = shift;
          packet.setUint8(offset, 2);
          packet.setUint8(offset + 1, input.pressed ? 1 : 0);
          packet.setUint8(offset + 2, input.keyCode);
          offset += 3;
        }
      }
      return offset;
    }
    toBuffer() {
      const metadataAlloc = new Uint8Array(this._totalSize);
      const packet = new DataView(metadataAlloc.buffer);
      packet.setUint16(0, this._reportType, true);
      packet.setUint32(2, this._sequence, true);
      packet.setFloat64(6, performance.now(), true);
      let offset = 14;
      if (this._metadataFrames.length > 0) {
        offset = this._writeMetadataData(packet, offset, this._metadataFrames);
      }
      if (this._gamepadFrames.length > 0) {
        offset = this._writeGamepadData(packet, offset, this._gamepadFrames);
      }
      if (this._pointerFrames.length > 0) {
        offset = this._writePointerData(packet, offset, this._pointerFrames);
      }
      if (this._mouseFrames.length > 0) {
        offset = this._writeMouseData(packet, offset, this._mouseFrames);
      }
      if (this._keyboardFrames.length > 0) {
        offset = this._writeKeyboardData(packet, offset, this._keyboardFrames);
      }
      if (this._reportType === 8 /* ClientMetadata */) {
        packet.setUint8(offset, this._maxTouchpoints);
        offset++;
      }
      if (offset !== offset) {
        throw new Error("Packet length mismatch. Something is wrong!");
      }
      return packet;
    }
    _normalizeTriggerValue(e) {
      if (e < 0) {
        return this._convertToUInt16(0);
      }
      const t = 65535 * e, a = t > 65535 ? 65535 : t;
      return this._convertToUInt16(a);
    }
    _normalizeAxisValue(e) {
      const t = this._convertToInt16(32767), a = this._convertToInt16(-32767), n = e * t;
      return n > t ? t : n < a ? a : this._convertToInt16(n);
    }
    _convertToInt16(e) {
      const int = new Int16Array(1);
      return int[0] = e, int[0];
    }
    _convertToUInt16(e) {
      const int = new Uint16Array(1);
      return int[0] = e, int[0];
    }
  };

  // src/Channel/Input.ts
  var InputChannel = class extends BaseChannel {
    _inputSequenceNum = 0;
    _reportTypes = {
      None: 0,
      Metadata: 1,
      Gamepad: 2,
      Pointer: 4,
      ClientMetadata: 8,
      ServerMetadata: 16,
      Mouse: 32,
      Keyboard: 64,
      Vibration: 128,
      Sendor: 256
    };
    _frameMetadataQueue = [];
    _gamepadFrames = [];
    _pointerFrames = [];
    _pointerCounter = 1;
    _mouseFrames = [];
    _keyboardFrames = [];
    _inputInterval;
    _keyboardEvents = [];
    _metadataFps;
    // _metadataLatency:LatencyCounter
    _inputFps;
    // _inputLatency:LatencyCounter
    _rumbleInterval = { 0: void 0, 1: void 0, 2: void 0, 3: void 0 };
    _rumbleEnabled = true;
    _adhocState;
    constructor(channelName, client) {
      super(channelName, client);
      this._metadataFps = new FpsCounter(this.getClient(), "metadata");
      this._inputFps = new FpsCounter(this.getClient(), "input");
    }
    onOpen(event) {
      super.onOpen(event);
      this._metadataFps.start();
      this._inputFps.start();
    }
    start() {
      const Packet = new InputPacket(this._inputSequenceNum);
      Packet.setMetadata(2);
      this.send(Packet.toBuffer());
      if (this._client._config.input_legacykeyboard === false) {
        this.getClient()._inputDriver.run();
      }
      this._inputInterval = setInterval(() => {
        if (this._client._config.input_legacykeyboard === true && this.getGamepadQueueLength() === 0) {
          const gpState = this.getClient()._inputDriver.requestStates();
          const kbState = this.getClient()._keyboardDriver.requestState();
          const mergedState = this.mergeState(gpState[0], kbState, this._adhocState);
          this._adhocState = null;
          this.queueGamepadState(mergedState);
          this._inputFps.count();
        }
        if (this._client._config.input_touch === true && Object.keys(this._touchEvents).length > 0) {
          for (const pointerEvent in this._touchEvents) {
            this._pointerFrames.push({
              events: this._touchEvents[pointerEvent].events
            });
          }
          this._touchEvents = {};
        }
        const metadataQueue = this.getMetadataQueue();
        const gamepadQueue = this.getGamepadQueue();
        const pointerQueue = this.getPointerQueue();
        const mouseQueue = this.getMouseQueue();
        const keyboardQueue = this.getKeyboardQueue();
        if (metadataQueue.length !== 0 || gamepadQueue.length !== 0 || pointerQueue.length !== 0) {
          this._inputSequenceNum++;
          const packet = new InputPacket(this._inputSequenceNum);
          packet.setData(metadataQueue, gamepadQueue, pointerQueue, mouseQueue, keyboardQueue);
          this._metadataFps.count();
          this.send(packet.toBuffer());
        }
      }, 16);
    }
    mergeState(gpState, kbState, adHoc) {
      return {
        GamepadIndex: gpState?.GamepadIndex ?? kbState.GamepadIndex,
        A: Math.max(gpState?.A ?? 0, kbState.A, adHoc?.A ?? 0),
        B: Math.max(gpState?.B ?? 0, kbState.B, adHoc?.B ?? 0),
        X: Math.max(gpState?.X ?? 0, kbState.X, adHoc?.X ?? 0),
        Y: Math.max(gpState?.Y ?? 0, kbState.Y, adHoc?.Y ?? 0),
        LeftShoulder: Math.max(gpState?.LeftShoulder ?? 0, kbState.LeftShoulder, adHoc?.LeftShoulder ?? 0),
        RightShoulder: Math.max(gpState?.RightShoulder ?? 0, kbState.RightShoulder, adHoc?.RightShoulder ?? 0),
        LeftTrigger: Math.max(gpState?.LeftTrigger ?? 0, kbState.LeftTrigger, adHoc?.LeftTrigger ?? 0),
        RightTrigger: Math.max(gpState?.RightTrigger ?? 0, kbState.RightTrigger, adHoc?.RightTrigger ?? 0),
        View: Math.max(gpState?.View ?? 0, kbState.View, adHoc?.View ?? 0),
        Menu: Math.max(gpState?.Menu ?? 0, kbState.Menu, adHoc?.Menu ?? 0),
        LeftThumb: Math.max(gpState?.LeftThumb ?? 0, kbState.LeftThumb, adHoc?.LeftThumb ?? 0),
        RightThumb: Math.max(gpState?.RightThumb ?? 0, kbState.RightThumb, adHoc?.RightThumb ?? 0),
        DPadUp: Math.max(gpState?.DPadUp ?? 0, kbState.DPadUp, adHoc?.DPadUp ?? 0),
        DPadDown: Math.max(gpState?.DPadDown ?? 0, kbState.DPadDown, adHoc?.DPadDown ?? 0),
        DPadLeft: Math.max(gpState?.DPadLeft ?? 0, kbState.DPadLeft, adHoc?.DPadLeft ?? 0),
        DPadRight: Math.max(gpState?.DPadRight ?? 0, kbState.DPadRight, adHoc?.DPadRight ?? 0),
        Nexus: Math.max(gpState?.Nexus ?? 0, kbState.Nexus, adHoc?.Nexus ?? 0),
        LeftThumbXAxis: this.mergeAxix(gpState?.LeftThumbXAxis ?? 0, kbState.LeftThumbXAxis),
        LeftThumbYAxis: this.mergeAxix(gpState?.LeftThumbYAxis ?? 0, kbState.LeftThumbYAxis),
        RightThumbXAxis: this.mergeAxix(gpState?.RightThumbXAxis ?? 0, kbState.RightThumbXAxis),
        RightThumbYAxis: this.mergeAxix(gpState?.RightThumbYAxis ?? 0, kbState.RightThumbYAxis)
      };
    }
    mergeAxix(axis1, axis2) {
      if (Math.abs(axis1) > Math.abs(axis2)) {
        return axis1;
      } else {
        return axis2;
      }
    }
    onMessage(event) {
      const dataView = new DataView(event.data);
      let i = 0;
      const reportType = dataView.getUint8(i);
      i += 2;
      if (reportType === this._reportTypes.Vibration) {
        dataView.getUint8(i);
        const gamepadIndex = dataView.getUint8(i + 1);
        i += 2;
        const leftMotorPercent = dataView.getUint8(i) / 100;
        const rightMotorPercent = dataView.getUint8(i + 1) / 100;
        const leftTriggerMotorPercent = dataView.getUint8(i + 2) / 100;
        const rightTriggerMotorPercent = dataView.getUint8(i + 3) / 100;
        const durationMs = dataView.getUint16(i + 4, true);
        const delayMs = dataView.getUint16(i + 6, true);
        const repeat = dataView.getUint8(i + 8);
        i += 9;
        const gamepad = navigator.getGamepads()[gamepadIndex];
        if (gamepad !== null && this._rumbleEnabled === true) {
          const rumbleData = {
            startDelay: 0,
            duration: durationMs,
            weakMagnitude: rightMotorPercent,
            strongMagnitude: leftMotorPercent,
            leftTrigger: leftTriggerMotorPercent,
            rightTrigger: rightTriggerMotorPercent
          };
          if (this._rumbleInterval[gamepadIndex] !== void 0) {
            clearInterval(this._rumbleInterval[gamepadIndex]);
          }
          if (gamepad.vibrationActuator !== void 0) {
            if (gamepad.vibrationActuator.type === "dual-rumble") {
              const intensityRumble = rightMotorPercent < 0.6 ? (0.6 - rightMotorPercent) / 2 : 0;
              const intensityRumbleTriggers = (leftTriggerMotorPercent + rightTriggerMotorPercent) / 4;
              const endIntensity = Math.min(intensityRumble, intensityRumbleTriggers);
              rumbleData.weakMagnitude = Math.min(1, rightMotorPercent + endIntensity);
              rumbleData.leftTrigger = 0;
              rumbleData.rightTrigger = 0;
            }
            gamepad.vibrationActuator?.playEffect(gamepad.vibrationActuator.type, rumbleData);
            if (repeat > 0) {
              let repeatCount = repeat;
              this._rumbleInterval[gamepadIndex] = setInterval(() => {
                if (repeatCount <= 0) {
                  clearInterval(this._rumbleInterval[gamepadIndex]);
                }
                if (gamepad.vibrationActuator !== void 0) {
                  gamepad.vibrationActuator?.playEffect(gamepad.vibrationActuator.type, rumbleData);
                }
                repeatCount--;
              }, delayMs + durationMs);
            }
          }
        }
      }
    }
    onClose(event) {
      clearInterval(this._inputInterval);
      super.onClose(event);
    }
    _createInputPacket(reportType, metadataQueue, gamepadQueue, pointerQueue, mouseQueue, keyboardQueue) {
      this._inputSequenceNum++;
      const Packet = new InputPacket(this._inputSequenceNum);
      Packet.setData(metadataQueue, gamepadQueue, pointerQueue, mouseQueue, keyboardQueue);
      return Packet.toBuffer();
    }
    getGamepadQueue(size = 30) {
      return this._gamepadFrames.splice(0, size - 1);
    }
    getGamepadQueueLength() {
      return this._gamepadFrames.length;
    }
    queueGamepadState(input) {
      if (input !== null) {
        return this._gamepadFrames.push(input);
      }
    }
    queueGamepadStates(inputs) {
      for (const input in inputs) {
        this.queueGamepadState(inputs[input]);
      }
    }
    getPointerQueue(size = 2) {
      return this._pointerFrames.splice(0, size - 1);
    }
    getPointerQueueLength() {
      return this._pointerFrames.length;
    }
    getMouseQueue(size = 30) {
      return this._mouseFrames.splice(0, size - 1);
    }
    getMouseQueueLength() {
      return this._mouseFrames.length;
    }
    getKeyboardQueue(size = 2) {
      return this._keyboardFrames.splice(0, size - 1);
    }
    getKeyboardQueueLength() {
      return this._keyboardFrames.length;
    }
    onPointerMove(e) {
      e.preventDefault();
      if (this._mouseActive === true && this._mouseLocked === true) {
        this._mouseStateX = e.movementX;
        this._mouseStateY = e.movementY;
        this._mouseStateButtons = e.buttons;
        this._mouseFrames.push({
          X: this._mouseStateX * 10,
          Y: this._mouseStateY * 10,
          WheelX: 0,
          WheelY: 0,
          Buttons: this._mouseStateButtons,
          Relative: 0
          // 0 = Relative, 1 = Absolute
        });
      }
      if (this._touchActive === true) {
        this._touchLastPointerId = e.pointerId;
        if (this._touchEvents[e.pointerId] === void 0) {
          this._touchEvents[e.pointerId] = {
            events: []
          };
        }
        this._touchEvents[e.pointerId].events.push(e);
      }
    }
    requestPointerLockWithUnadjustedMovement(element) {
      const promise = element.requestPointerLock({
        unadjustedMovement: true
      });
      if ("keyboard" in navigator && "lock" in navigator.keyboard) {
        document.body.requestFullscreen().then(() => {
          navigator.keyboard.lock();
        });
      }
      return promise.then(() => {
        console.log("pointer is locked");
        this._mouseLocked = true;
      }).catch((error) => {
        if (error.name === "NotSupportedError") {
          this._mouseLocked = true;
          return element.requestPointerLock();
        }
      });
    }
    _touchEvents = {};
    _touchLastPointerId = 0;
    onPointerClick(e) {
      e.preventDefault();
      if (e.pointerType === "touch") {
        this._mouseActive = false;
        this._touchActive = true;
      } else if (e.pointerType === "mouse") {
        this._mouseActive = true;
        this._touchActive = false;
      }
      if (this._client._config.input_mousekeyboard === true && this._mouseActive === true && this._mouseLocked === false) {
        this.requestPointerLockWithUnadjustedMovement(e.target);
        document.addEventListener("pointerlockchange", () => {
          if (document.pointerLockElement !== null) {
            this._mouseLocked = true;
          } else {
            this._mouseLocked = false;
          }
        }, false);
        document.addEventListener("systemkeyboardlockchanged", (event) => {
          console.log(event);
        }, false);
      } else if (this._mouseActive === true && this._mouseLocked === true) {
        this._mouseStateX = e.movementX;
        this._mouseStateY = e.movementY;
        this._mouseStateButtons = e.buttons;
        this._mouseFrames.push({
          X: this._mouseStateX * 10,
          Y: this._mouseStateY * 10,
          WheelX: 0,
          WheelY: 0,
          Buttons: this._mouseStateButtons,
          Relative: 0
          // 0 = Relative, 1 = Absolute
        });
      }
      if (this._touchActive === true) {
        this._touchLastPointerId = e.pointerId;
        if (this._touchEvents[e.pointerId] === void 0) {
          this._touchEvents[e.pointerId] = {
            events: []
          };
        }
        this._touchEvents[e.pointerId].events.push(e);
      }
    }
    onPointerScroll(e) {
      e.preventDefault();
    }
    _mouseActive = false;
    _mouseLocked = false;
    _touchActive = false;
    _mouseStateButtons = 0;
    _mouseStateX = 0;
    _mouseStateY = 0;
    onKeyDown(event) {
      if (this._mouseActive === true && this._mouseLocked === true) {
        if (this._keyboardButtonsState[event.keyCode] !== true) {
          this._keyboardButtonsState[event.keyCode] = true;
          this._keyboardFrames.push({
            pressed: true,
            key: event.key,
            keyCode: event.keyCode
          });
          setTimeout(() => {
            this._keyboardFrames.push({
              pressed: true,
              key: event.key,
              keyCode: event.keyCode
            });
          }, 16);
        }
      }
    }
    onKeyUp(event) {
      if (this._mouseActive === true && this._mouseLocked === true) {
        this._keyboardButtonsState[event.keyCode] = false;
        this._keyboardFrames.push({
          pressed: false,
          key: event.key,
          keyCode: event.keyCode
        });
        setTimeout(() => {
          this._keyboardFrames.push({
            pressed: false,
            key: event.key,
            keyCode: event.keyCode
          });
        }, 16);
      }
    }
    _keyboardButtonsState = {};
    convertAbsoluteMousePositionImpl(e, t, i, n) {
      let s = i;
      let a = n;
      const o = 1920 / 1080;
      if (o > i / n) {
        a = s / o;
        t -= (n - a) / 2;
      } else {
        s = a * o;
        e -= (i - s) / 2;
      }
      e *= 65535 / s;
      t *= 65535 / a;
      return [e = Math.min(Math.max(Math.round(e), 0), 65535), t = Math.min(Math.max(Math.round(t), 0), 65535)];
    }
    _convertToInt16(e) {
      const int = new Int16Array(1);
      return int[0] = e, int[0];
    }
    _convertToUInt16(e) {
      const int = new Uint16Array(1);
      return int[0] = e, int[0];
    }
    normalizeTriggerValue(e) {
      if (e < 0) {
        return this._convertToUInt16(0);
      }
      const t = 65535 * e, a = t > 65535 ? 65535 : t;
      return this._convertToUInt16(a);
    }
    normalizeAxisValue(e) {
      const t = this._convertToInt16(32767), a = this._convertToInt16(-32767), n = e * t;
      return n > t ? t : n < a ? a : this._convertToInt16(n);
    }
    pressButton(index, button) {
      if (this._client._config.input_legacykeyboard === true) {
        this._client._keyboardDriver.pressButton(button);
      } else {
        this._client._inputDriver.pressButton(index, button);
      }
    }
    destroy() {
      this._metadataFps.stop();
      this._inputFps.stop();
      clearInterval(this._inputInterval);
      super.destroy();
    }
    addProcessedFrame(frame) {
      frame.frameRenderedTimeMs = performance.now();
      this._frameMetadataQueue.push(frame);
    }
    getMetadataQueue(size = 30) {
      return this._frameMetadataQueue.splice(0, size - 1);
    }
    getMetadataQueueLength() {
      return this._frameMetadataQueue.length;
    }
  };

  // src/Channel/Control.ts
  var ControlChannel = class extends BaseChannel {
    onOpen(event) {
      super.onOpen(event);
    }
    start() {
      const authRequest = JSON.stringify({
        "message": "authorizationRequest",
        "accessKey": "4BDB3609-C1F1-4195-9B37-FEFF45DA8B8E"
      });
      this.send(authRequest);
      this._client._inputDriver.start();
      this._client._keyboardDriver.start();
      this.sendGamepadAdded(0);
    }
    sendGamepadAdded(gamepadIndex) {
      const gamepadRequest = JSON.stringify({
        "message": "gamepadChanged",
        "gamepadIndex": gamepadIndex,
        "wasAdded": true
      });
      this.send(gamepadRequest);
    }
    sendGamepadRemoved(gamepadIndex) {
      const gamepadRequest = JSON.stringify({
        "message": "gamepadChanged",
        "gamepadIndex": gamepadIndex,
        "wasAdded": false
      });
      this.send(gamepadRequest);
    }
    onMessage(event) {
      console.log("xCloudPlayer Channel/Control.ts - [" + this._channelName + "] onMessage:", event);
      const jsonMessage = JSON.parse(event.data);
      console.log("xCloudPlayer Channel/Control.ts - Received json:", jsonMessage);
    }
    onClose(event) {
      super.onClose(event);
      this._client._inputDriver.stop();
      this._client._keyboardDriver.stop();
    }
    requestKeyframeRequest() {
      console.log("xCloudPlayer Channel/Control.ts - [" + this._channelName + "] User requested Video KeyFrame");
      const keyframeRequest = JSON.stringify({
        message: "videoKeyframeRequested",
        ifrRequested: true
      });
      this.send(keyframeRequest);
    }
  };

  // src/Channel/Message.ts
  var MessageChannel = class extends BaseChannel {
    onOpen(event) {
      super.onOpen(event);
      const handshake = JSON.stringify({
        "type": "Handshake",
        "version": "messageV1",
        "id": "f9c5f412-0e69-4ede-8e62-92c7f5358c56",
        "cv": ""
      });
      this.send(handshake);
    }
    onMessage(event) {
      console.log("xCloudPlayer Channel/Message.ts - [" + this._channelName + "] onMessage:", event);
      const jsonMessage = JSON.parse(event.data);
      console.log("xCloudPlayer Channel/Message.ts - Received json:", jsonMessage);
      if (jsonMessage.type === "HandshakeAck") {
        this.getClient().getChannelProcessor("control").start();
        this.getClient().getChannelProcessor("input").start();
        const systemUis = this.getClient()._config.ui_systemui || [10, 19, 31, 27, 32, -41];
        const systemVersion = this.getClient()._config.ui_version || [0, 1, 0];
        const uiConfig = JSON.stringify(this.generateMessage("/streaming/systemUi/configuration", {
          "version": systemVersion,
          "systemUis": systemUis
          // Xbox Windows app has [33], xCloud has [10,19,31,27,32,-41]
          // 10 = ShowVirtualKeyboard
          // 19 = ShowMessageDialog
          // 31 = ShowApplication
          // 27 = ShowPurchase
          // 32 = ShowTimerExtensions
          // 33 = Xbox windows app, disables the nexus menu on xCloud (Alt nexus menu?)
          // -44 = unknown
          // 40 = unknown
          // 41 = unknown
          // -43 = unknown
          // Possible options: Keyboard, PurchaseModal
        }));
        this.send(uiConfig);
        const clientConfig = JSON.stringify(this.generateMessage("/streaming/properties/clientappinstallidchanged", { "clientAppInstallId": "c11ddb2e-c7e3-4f02-a62b-fd5448e0b851" }));
        this.send(clientConfig);
        const orientationConfig = JSON.stringify(this.generateMessage("/streaming/characteristics/orientationchanged", { "orientation": 0 }));
        this.send(orientationConfig);
        const touchConfig = JSON.stringify(this.generateMessage("/streaming/characteristics/touchinputenabledchanged", { "touchInputEnabled": this.getClient()._config.input_touch }));
        this.send(touchConfig);
        const deviceConfig = JSON.stringify(this.generateMessage("/streaming/characteristics/clientdevicecapabilities", {}));
        this.send(deviceConfig);
        const dimensionsConfig = JSON.stringify(this.generateMessage("/streaming/characteristics/dimensionschanged", {
          "horizontal": 1920,
          "vertical": 1080,
          "preferredWidth": 1920,
          "preferredHeight": 1080,
          "safeAreaLeft": 0,
          "safeAreaTop": 0,
          "safeAreaRight": 1920,
          "safeAreaBottom": 1080,
          "supportsCustomResolution": true
        }));
        this.send(dimensionsConfig);
      }
      this.getClient().getEventBus().emit("message", {
        ...jsonMessage
      });
    }
    onClose(event) {
      super.onClose(event);
    }
    generateMessage(path, data) {
      return {
        "type": "Message",
        "content": JSON.stringify(data),
        "id": "41f93d5a-900f-4d33-b7a1-2d4ca6747072",
        "target": path,
        "cv": ""
      };
    }
    sendTransaction(id, data) {
      const transaction = JSON.stringify({
        "type": "TransactionComplete",
        "content": JSON.stringify(data),
        // 'content':'{\'Result\':0}',
        "id": id,
        "cv": ""
      });
      this.send(transaction);
    }
  };

  // src/Channel/Chat.ts
  var ChatChannel = class extends BaseChannel {
    isCapturing = false;
    isPaused = true;
    onOpen(event) {
      super.onOpen(event);
    }
    start() {
    }
    onMessage(event) {
      console.log("xCloudPlayer Channel/Chat.ts - [" + this._channelName + "] onMessage:", event);
      const jsonMessage = JSON.parse(event.data);
      console.log("xCloudPlayer Channel/Chat.ts - Received json:", jsonMessage);
    }
    onClose(event) {
      super.onClose(event);
    }
    startMic() {
      console.log("xCloudPlayer Channel/Chat.ts - Enabling Microphone");
      if (this.isCapturing === false) {
        console.log("Start chat...");
        navigator.mediaDevices.getUserMedia({
          audio: {
            channelCount: 1,
            sampleRate: 24e3
          }
        }).then((stream) => {
          this.isCapturing = true;
          const audioTracks = stream.getAudioTracks();
          if (audioTracks.length > 0) {
            console.log(`Using Audio device: ${audioTracks[0].label}`);
          } else {
            console.log("No Audio device:", audioTracks);
          }
          stream.getTracks().forEach((track) => {
            this._client._webrtcClient?.addTrack(track, stream);
          });
          this._client.sdpNegotiationChat();
        }).catch((e) => {
          alert(`getUserMedia() error: ${e.name}`);
          this.isCapturing = false;
        });
      }
      this.isPaused = false;
    }
    stopMic() {
      console.log("xCloudPlayer Channel/Chat.ts - Disabling Microphone");
      const senders = this._client._webrtcClient?.getSenders();
      for (const sender in senders) {
        if (senders[sender].track !== null) {
          if (senders[sender].track?.kind === "audio") {
            this._client._webrtcClient?.removeTrack(senders[sender]);
          }
        }
      }
      this.isCapturing = false;
      this.isPaused = true;
    }
  };

  // src/Component/Video.ts
  var VideoComponent = class {
    _client;
    _videoSource;
    _mediaSource;
    _videoRender;
    _focusEvent;
    _framekeyInterval;
    _videoFps;
    constructor(client) {
      this._client = client;
    }
    create(srcObject) {
      console.log("xCloudPlayer Component/Video.ts - Create media element");
      this._videoFps = new FpsCounter(this._client, "video");
      const videoHolder = document.getElementById(this._client._elementHolder);
      if (videoHolder !== null) {
        const videoRender = document.createElement("video");
        videoRender.id = this.getElementId();
        videoRender.srcObject = srcObject;
        videoRender.width = videoHolder.clientWidth;
        videoRender.height = videoHolder.clientHeight;
        videoRender.style.touchAction = "none";
        videoRender.autoplay = true;
        videoRender.setAttribute("playsinline", "playsinline");
        videoRender.onclick = () => {
          videoRender.play();
          this._client._audioComponent._audioRender.play();
        };
        const serverDataLoop = (t, i) => {
          videoRender.requestVideoFrameCallback(serverDataLoop);
          this._videoFps.count();
          this._client.getChannelProcessor("input").addProcessedFrame({
            serverDataKey: i.rtpTimestamp,
            firstFramePacketArrivalTimeMs: i.receiveTime,
            frameSubmittedTimeMs: i.receiveTime,
            frameDecodedTimeMs: i.expectedDisplayTime,
            frameRenderedTimeMs: i.expectedDisplayTime
          });
        };
        videoRender.requestVideoFrameCallback(serverDataLoop);
        this._videoRender = videoRender;
        videoHolder.appendChild(videoRender);
        this._videoFps.start();
        videoRender.addEventListener("pointermove", (e) => this._client.getChannelProcessor("input").onPointerMove(e), { passive: false }), videoRender.addEventListener("pointerdown", (e) => this._client.getChannelProcessor("input").onPointerClick(e), { passive: false }), videoRender.addEventListener("pointerup", (e) => this._client.getChannelProcessor("input").onPointerClick(e), { passive: false }), videoRender.addEventListener("wheel", (e) => this._client.getChannelProcessor("input").onPointerScroll(e), { passive: false });
        window.addEventListener("keydown", (e) => {
          this._client.getChannelProcessor("input").onKeyDown(e);
        });
        window.addEventListener("keyup", (e) => {
          this._client.getChannelProcessor("input").onKeyUp(e);
        });
        videoRender.play().then(() => {
        }).catch((error) => {
          console.log("xCloudPlayer Component/Video.ts - Error executing play() on videoRender:", error);
        });
      } else {
        console.log("xCloudPlayer Component/Video.ts - Error fetching videoholder: div#" + this._client._elementHolder);
      }
      console.log("xCloudPlayer Component/Video.ts - Media element created");
    }
    getElementId() {
      return "xCloudPlayer_" + this._client._elementHolderRandom + "_videoRender";
    }
    getSource() {
      return this._videoSource;
    }
    createMediaSource() {
      const mediaSource = new MediaSource();
      const videoSourceUrl = window.URL.createObjectURL(mediaSource);
      mediaSource.addEventListener("sourceopen", () => {
        console.log("xCloudPlayer Component/Video.ts - MediaSource opened. Attaching videoSourceBuffer...");
        const videoSourceBuffer = mediaSource.addSourceBuffer('video/mp4; codecs="avc1.42c020"');
        videoSourceBuffer.mode = "sequence";
        videoSourceBuffer.addEventListener("error", (event) => {
          console.log("xCloudPlayer Component/Video.ts - Error video...", event);
        });
        this._videoSource = videoSourceBuffer;
      });
      this._mediaSource = mediaSource;
      return videoSourceUrl;
    }
    destroy() {
      if (this._videoRender) {
        this._videoRender.pause();
        this._videoRender.remove();
      }
      this._videoFps.stop();
      delete this._mediaSource;
      delete this._videoRender;
      delete this._videoSource;
      document.getElementById(this.getElementId())?.remove();
      console.log("xCloudPlayer Component/Video.ts - Cleaning up Video element");
    }
  };

  // src/Component/Audio.ts
  var AudioComponent = class {
    _client;
    _audioSource;
    _mediaSource;
    _audioRender;
    constructor(client) {
      this._client = client;
    }
    create(srcObject) {
      console.log("xCloudPlayer Component/Audio.ts - Create media element");
      const audioHolder = document.getElementById(this._client._elementHolder);
      if (audioHolder !== null) {
        const audioRender = document.createElement("audio");
        audioRender.id = this.getElementId();
        audioRender.srcObject = srcObject;
        audioRender.autoplay = true;
        this._audioRender = audioRender;
        audioHolder.appendChild(audioRender);
      } else {
        console.log("xCloudPlayer Component/Audio.ts - Error fetching audioholder: div#" + this._client._elementHolder);
      }
      console.log("xCloudPlayer Component/Audio.ts - Media element created");
    }
    getElementId() {
      return "xCloudPlayer_" + this._client._elementHolderRandom + "_audioRender";
    }
    getSource() {
      return this._audioSource;
    }
    createMediaSource() {
      const mediaSource = new MediaSource();
      const audioSourceUrl = window.URL.createObjectURL(mediaSource);
      mediaSource.addEventListener("sourceopen", () => {
        console.log("xCloudPlayer Component/Audio.ts - MediaSource opened. Attaching audioSourceBuffer...");
        let codec = "audio/webm;codecs=opus";
        if (this._isSafari()) {
          codec = "audio/mp4";
        }
        const audioSourceBuffer = mediaSource.addSourceBuffer(codec);
        audioSourceBuffer.mode = "sequence";
        audioSourceBuffer.addEventListener("error", (event) => {
          console.log("xCloudPlayer Component/Audio.ts - Error audio...", event);
        });
        this._audioSource = audioSourceBuffer;
      });
      this._mediaSource = mediaSource;
      return audioSourceUrl;
    }
    destroy() {
      if (this._audioRender) {
        this._audioRender.pause();
        this._audioRender.remove();
      }
      delete this._mediaSource;
      delete this._audioRender;
      delete this._audioSource;
      document.getElementById(this.getElementId())?.remove();
      console.log("xCloudPlayer Component/Audio.ts - Cleaning up audio element");
    }
    _isSafari() {
      return navigator.userAgent.search("Safari") >= 0 && navigator.userAgent.search("Chrome") < 0;
    }
  };

  // src/Helper/EventBus.ts
  var import_events = __toESM(require_events());
  var EventBus = class extends import_events.default {
  };

  // src/Driver/Gamepad.ts
  var KEYCODE_KEY_N = "n";
  var GamepadDriver = class {
    _application = null;
    _shadowGamepad = {
      0: {
        A: 0,
        B: 0,
        X: 0,
        Y: 0,
        LeftShoulder: 0,
        RightShoulder: 0,
        LeftTrigger: 0,
        RightTrigger: 0,
        View: 0,
        Menu: 0,
        LeftThumb: 0,
        RightThumb: 0,
        DPadUp: 0,
        DPadDown: 0,
        DPadLeft: 0,
        DPadRight: 0,
        Nexus: 0,
        LeftThumbXAxis: 0,
        LeftThumbYAxis: 0,
        RightThumbXAxis: 0,
        RightThumbYAxis: 0
      }
    };
    _activeGamepads = { 0: false, 1: false, 2: false, 3: false };
    _activeGamepadsInterval;
    _nexusOverrideN = false;
    // constructor() {
    // }
    setApplication(application) {
      this._application = application;
    }
    start() {
      this._activeGamepads = { 0: false, 1: false, 2: false, 3: false };
      this._activeGamepadsInterval = setInterval(() => {
        const gamepads = navigator.getGamepads();
        for (let gamepad = 0; gamepad < gamepads.length; gamepad++) {
          if (gamepad === 0) {
            return;
          }
          if (this._application?.getChannelProcessor("control") === void 0) {
            return;
          }
          if (gamepads[gamepad] === null && this._activeGamepads[gamepad] === true) {
            this._application?.getChannelProcessor("control").sendGamepadRemoved(gamepad);
            this._activeGamepads[gamepad] = false;
            return;
          }
          if (gamepads[gamepad] !== null && this._activeGamepads[gamepad] === false) {
            this._application?.getChannelProcessor("control").sendGamepadAdded(gamepad);
            this._activeGamepads[gamepad] = true;
            return;
          }
        }
      }, 500);
      window.addEventListener("keydown", this._downFunc);
      window.addEventListener("keyup", this._upFunc);
    }
    stop() {
      clearInterval(this._activeGamepadsInterval);
      window.removeEventListener("keydown", this._downFunc);
      window.removeEventListener("keyup", this._upFunc);
    }
    _downFunc = (e) => {
      this.onKeyChange(e, true);
    };
    _upFunc = (e) => {
      this.onKeyChange(e, false);
    };
    onKeyChange(e, down) {
      switch (e.key) {
        case KEYCODE_KEY_N:
          this._nexusOverrideN = down;
          break;
      }
    }
    pressButton(index, button) {
      this._shadowGamepad[index][button] = 1;
      this._application?.getChannelProcessor("input").queueGamepadState(this._shadowGamepad[index]);
      setTimeout(() => {
        this._shadowGamepad[index][button] = 0;
        this._application?.getChannelProcessor("input").queueGamepadState(this._shadowGamepad[index]);
      }, 60);
    }
    // Only ran when new gamepad driver is selected
    run() {
      const gpState = this.requestStates();
      if (gpState[0] !== void 0) {
        if (this._nexusOverrideN === true) {
          gpState[0].Nexus = 1;
        }
      }
      this._application?.getChannelProcessor("input")._inputFps.count();
      this._application?.getChannelProcessor("input").queueGamepadStates(gpState);
      setTimeout(() => {
        this.run();
      }, 1e3 / 60);
    }
    requestStates() {
      const gamepads = navigator.getGamepads();
      const states = [];
      for (let gamepad = 0; gamepad < gamepads.length; gamepad++) {
        const gamepadState = gamepads[gamepad];
        if (gamepadState !== null && gamepadState.connected) {
          const state = this.mapStateLabels(gamepadState.buttons, gamepadState.axes);
          state.GamepadIndex = gamepadState.index;
          states.push(state);
        }
      }
      return states;
    }
    mapStateLabels(buttons, axes) {
      return {
        A: buttons[0]?.value || this._shadowGamepad[0].A || 0,
        B: buttons[1]?.value || this._shadowGamepad[0].B || 0,
        X: buttons[2]?.value || this._shadowGamepad[0].X || 0,
        Y: buttons[3]?.value || this._shadowGamepad[0].Y || 0,
        LeftShoulder: buttons[4]?.value || 0,
        RightShoulder: buttons[5]?.value || 0,
        LeftTrigger: buttons[6]?.value || 0,
        RightTrigger: buttons[7]?.value || 0,
        View: buttons[8]?.value || 0,
        Menu: buttons[9]?.value || 0,
        LeftThumb: buttons[10]?.value || 0,
        RightThumb: buttons[11]?.value || 0,
        DPadUp: buttons[12]?.value || 0,
        DPadDown: buttons[13]?.value || 0,
        DPadLeft: buttons[14]?.value || 0,
        DPadRight: buttons[15]?.value || 0,
        Nexus: buttons[16]?.value || buttons[8]?.value && buttons[9]?.value || this._shadowGamepad[0].Nexus || 0,
        LeftThumbXAxis: axes[0],
        LeftThumbYAxis: axes[1],
        RightThumbXAxis: axes[2],
        RightThumbYAxis: axes[3]
      };
    }
  };

  // src/Driver/Keyboard.ts
  var KEYCODE_ARROW_LEFT = "ArrowLeft";
  var KEYCODE_ARROW_UP = "ArrowUp";
  var KEYCODE_ARROW_RIGHT = "ArrowRight";
  var KEYCODE_ARROW_DOWN = "ArrowDown";
  var KEYCODE_KEY_A = "a";
  var KEYCODE_ENTER = "Enter";
  var KEYCODE_KEY_B = "b";
  var KEYCODE_BACKSPACE = "Backspace";
  var KEYCODE_KEY_X = "x";
  var KEYCODE_KEY_Y = "y";
  var KEYCODE_KEY_N2 = "n";
  var KEYCODE_KEY_LEFT_BRACKET = "[";
  var KEYCODE_KEY_RIGHT_BRACKET = "]";
  var KEYCODE_KEY_V = "v";
  var KEYCODE_KEY_M = "m";
  var KEYCODE_MINUS = "-";
  var KEYCODE_EQUALS = "=";
  var MouseKeyboardConfig = class _MouseKeyboardConfig {
    _keymapping;
    constructor(args) {
      if (args.keymapping === void 0) {
        this._keymapping = _MouseKeyboardConfig.defaultMapping();
      } else {
        this._keymapping = args.keymapping;
      }
    }
    static defaultMapping() {
      return {
        [KEYCODE_ARROW_LEFT]: "DPadLeft",
        [KEYCODE_ARROW_UP]: "DPadUp",
        [KEYCODE_ARROW_RIGHT]: "DPadRight",
        [KEYCODE_ARROW_DOWN]: "DPadDown",
        [KEYCODE_ENTER]: "A",
        [KEYCODE_KEY_A]: "A",
        [KEYCODE_BACKSPACE]: "B",
        [KEYCODE_KEY_B]: "B",
        [KEYCODE_KEY_X]: "X",
        [KEYCODE_KEY_Y]: "Y",
        [KEYCODE_KEY_LEFT_BRACKET]: "LeftShoulder",
        [KEYCODE_KEY_RIGHT_BRACKET]: "RightShoulder",
        [KEYCODE_MINUS]: "LeftTrigger",
        [KEYCODE_EQUALS]: "RightTrigger",
        [KEYCODE_KEY_V]: "View",
        [KEYCODE_KEY_M]: "Menu",
        [KEYCODE_KEY_N2]: "Nexus"
      };
    }
    static default() {
      return new _MouseKeyboardConfig({});
    }
  };
  var KeyboardDriver = class {
    _mouseKeyboardConfig;
    constructor(mouseKeyboardConfig) {
      this._mouseKeyboardConfig = mouseKeyboardConfig;
      console.log("MouseConfig /// ", this._mouseKeyboardConfig);
    }
    _keyboardState = {
      GamepadIndex: 0,
      Nexus: 0,
      Menu: 0,
      View: 0,
      A: 0,
      B: 0,
      X: 0,
      Y: 0,
      DPadUp: 0,
      DPadDown: 0,
      DPadLeft: 0,
      DPadRight: 0,
      LeftShoulder: 0,
      RightShoulder: 0,
      LeftThumb: 0,
      RightThumb: 0,
      LeftThumbXAxis: 0,
      LeftThumbYAxis: 0,
      RightThumbXAxis: 0,
      RightThumbYAxis: 0,
      LeftTrigger: 0,
      RightTrigger: 0
    };
    _downFunc = (e) => {
      this.onKeyChange(e, true);
    };
    _upFunc = (e) => {
      this.onKeyChange(e, false);
    };
    start() {
      window.addEventListener("keydown", this._downFunc);
      window.addEventListener("keyup", this._upFunc);
    }
    stop() {
      window.removeEventListener("keydown", this._downFunc);
      window.removeEventListener("keyup", this._upFunc);
    }
    onKeyDown(e) {
      this.onKeyChange(e, true);
    }
    onKeyUp(e) {
      this.onKeyChange(e, false);
    }
    onKeyChange(e, down) {
      const val = down ? 1 : 0;
      const mappedButton = this._mouseKeyboardConfig._keymapping[e.key];
      if (mappedButton === void 0) {
        return;
      }
      this._keyboardState[mappedButton] = val;
    }
    requestState() {
      return this._keyboardState;
    }
    pressButton(button) {
      this._keyboardState[button] = 1;
      setTimeout(() => {
        this._keyboardState[button] = 0;
      }, 60);
    }
  };

  // src/Library.ts
  var xCloudPlayerBackend = class {
    _config = {
      locale: "en-US"
    };
    sessionId = "";
    setSessionId(sessionId) {
      this.sessionId = sessionId;
    }
    getConsoles() {
      return this.readBody(fetch("/v6/servers/home"));
    }
    startSession(type, sessionId) {
      return new Promise((resolve, reject) => {
        const deviceInfo = JSON.stringify({
          "appInfo": {
            "env": {
              "clientAppId": "Microsoft.GamingApp",
              "clientAppType": "native",
              "clientAppVersion": "2203.1001.4.0",
              "clientSdkVersion": "8.5.2",
              "httpEnvironment": "prod",
              "sdkInstallId": ""
            }
          },
          "dev": {
            "hw": {
              "make": "Microsoft",
              "model": "Surface Pro",
              "sdktype": "native"
            },
            "os": {
              "name": "Windows 11",
              "ver": "22631.2715",
              "platform": "desktop"
            },
            "displayInfo": {
              "dimensions": {
                "widthInPixels": 1920,
                "heightInPixels": 1080
              },
              "pixelDensity": {
                "dpiX": 1,
                "dpiY": 1
              }
            }
          }
        });
        this.readBody(fetch("/v5/sessions/" + type + "/play", {
          method: "POST",
          headers: {
            "Accept": "application/json",
            "Content-Type": "application/json",
            "X-MS-Device-Info": deviceInfo
          },
          body: JSON.stringify({
            clientSessionId: "",
            titleId: type === "cloud" ? sessionId : "",
            systemUpdateGroup: "",
            settings: {
              nanoVersion: "V3;WebrtcTransport.dll",
              enableTextToSpeech: false,
              highContrast: 0,
              locale: this._config.locale,
              useIceConnection: false,
              timezoneOffsetMinutes: 120,
              sdkType: "web",
              osName: "windows"
            },
            serverId: type === "home" ? sessionId : "",
            fallbackRegionNames: []
          })
        })).then((response) => {
          console.log("Started streaming session:", response.sessionId);
          this.setSessionId(response.sessionId);
          this.waitState().then(() => {
            this.readBody(fetch("/v5/sessions/home/" + this.sessionId + "/configuration")).then((configuration) => {
              resolve(configuration);
            }).catch((error) => {
              reject(error);
            });
          }).catch((error) => {
            reject(error);
          });
        }).catch((error) => {
          reject(error);
        });
      });
    }
    waitState() {
      return new Promise((resolve, reject) => {
        this.readBody(fetch("/v5/sessions/home/" + this.sessionId + "/state")).then((state) => {
          switch (state.state) {
            case "Provisioned":
              resolve(state);
              break;
            case "Provisioning":
              setTimeout(() => {
                this.waitState().then((state2) => {
                  resolve(state2);
                }).catch((error) => {
                  reject(error);
                });
              }, 2e3);
              break;
            default:
              console.log("unknown state:", state);
              break;
          }
        }).catch((error) => {
          reject(error);
        });
      });
    }
    sendSDPOffer(sdpOffer) {
      return new Promise((resolve, reject) => {
        fetch("/v5/sessions/home/" + this.sessionId + "/sdp", {
          method: "POST",
          headers: {
            "Accept": "application/json",
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            "messageType": "offer",
            "sdp": sdpOffer.sdp,
            "configuration": {
              "chatConfiguration": {
                "bytesPerSample": 2,
                "expectedClipDurationMs": 20,
                "format": {
                  "codec": "opus",
                  "container": "webm"
                },
                "numChannels": 1,
                "sampleFrequencyHz": 24e3
              },
              "chat": {
                "minVersion": 1,
                "maxVersion": 1
              },
              "control": {
                "minVersion": 1,
                "maxVersion": 3
              },
              "input": {
                "minVersion": 1,
                "maxVersion": 8
              },
              "message": {
                "minVersion": 1,
                "maxVersion": 1
              }
            }
          })
        }).then(() => {
          this.readBody(fetch("/v5/sessions/home/" + this.sessionId + "/sdp")).then((sdpResponse) => {
            if (sdpResponse === "retry") {
              const checkInterval = setInterval(() => {
                this.readBody(fetch("/v5/sessions/home/" + this.sessionId + "/sdp")).then((sdpResponse2) => {
                  if (sdpResponse2 !== "retry") {
                    resolve(sdpResponse2);
                    clearInterval(checkInterval);
                  }
                }).catch((error) => {
                  reject(error);
                  clearInterval(checkInterval);
                });
              }, 1e3);
            }
            resolve(sdpResponse);
          }).catch((error) => {
            reject(error);
          });
        }).catch((error) => {
          reject(error);
        });
      });
    }
    sendSDPChatOffer(sdpOffer) {
      return new Promise((resolve, reject) => {
        fetch("/v5/sessions/home/" + this.sessionId + "/sdp", {
          method: "POST",
          headers: {
            "Accept": "application/json",
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            "messageType": "offer",
            "requestId": 2,
            "sdp": sdpOffer.sdp,
            "configuration": {
              "isMediaStreamsChatRenegotiation": true
            }
          })
        }).then(() => {
          this.readBody(fetch("/v5/sessions/home/" + this.sessionId + "/sdp")).then((sdpResponse) => {
            if (sdpResponse === "retry") {
              const checkInterval = setInterval(() => {
                this.readBody(fetch("/v5/sessions/home/" + this.sessionId + "/sdp")).then((sdpResponse2) => {
                  if (sdpResponse2 !== "retry") {
                    resolve(sdpResponse2);
                    clearInterval(checkInterval);
                  }
                }).catch((error) => {
                  reject(error);
                  clearInterval(checkInterval);
                });
              }, 1e3);
            }
            resolve(sdpResponse);
          }).catch((error) => {
            reject(error);
          });
        }).catch((error) => {
          reject(error);
        });
      });
    }
    sendICECandidates(iceCandidates) {
      return new Promise((resolve, reject) => {
        fetch("/v5/sessions/home/" + this.sessionId + "/ice", {
          method: "POST",
          headers: {
            "Accept": "application/json",
            "Content-Type": "application/json"
          },
          body: JSON.stringify({
            iceCandidates
          })
        }).then(() => {
          this.readBody(fetch("/v5/sessions/home/" + this.sessionId + "/ice")).then((iceResponse) => {
            resolve(iceResponse);
          }).catch((error) => {
            reject(error);
          });
        }).catch((error) => {
          reject(error);
        });
      });
    }
    readBody(fetchparam) {
      return new Promise((resolve, reject) => {
        fetchparam.then((response) => {
          if (response.status === 204) {
            resolve("retry");
          } else {
            response.json().then((data) => {
              resolve(data);
            }).catch((error) => {
              reject({ error });
            });
          }
        });
      });
    }
  };
  var xCloudPlayer = class {
    _config;
    _webrtcClient;
    _eventBus;
    _isResetting = false;
    _webrtcConfiguration = {
      iceServers: [{
        urls: "stun:stun.l.google.com:19302"
      }, {
        urls: "stun:stun1.l.google.com:19302"
      }]
    };
    _webrtcDataChannelsConfig = {
      "input": {
        ordered: true,
        protocol: "1.0"
      },
      "chat": {
        protocol: "chatV1"
      },
      "control": {
        protocol: "controlV1"
      },
      "message": {
        protocol: "messageV1"
      }
    };
    _webrtcStates = {
      iceGathering: "open",
      iceConnection: "open",
      iceCandidates: [],
      streamConnection: "open"
    };
    _webrtcDataChannels = {};
    _webrtcChannelProcessors = {};
    _iceCandidates = [];
    _elementHolder;
    _elementHolderRandom;
    _inputDriver = void 0;
    _keyboardDriver = void 0;
    _videoComponent;
    _audioComponent;
    _codecPreference = "";
    _codecProfiles = [];
    _maxVideoBitrate = 0;
    _maxAudioBitrate = 0;
    constructor(elementId, config = {}) {
      console.log("xCloudPlayer loaded!");
      this._config = Object.assign({
        input_touch: false,
        input_mousekeyboard: false,
        input_legacykeyboard: true
      }, config);
      this._eventBus = new EventBus();
      this._elementHolder = elementId;
      this._elementHolderRandom = Math.floor(Math.random() * 100) + 1;
    }
    bind() {
      this._webrtcClient = new RTCPeerConnection(this._webrtcConfiguration);
      this._openDataChannels();
      if (this._config.input_driver === void 0) {
        this._inputDriver = new GamepadDriver();
      } else if (this._config.input_driver !== null) {
        this._inputDriver = this._config.input_driver;
      }
      this._inputDriver.setApplication(this);
      this._keyboardDriver = new KeyboardDriver(this._config.input_mousekeyboard_config ?? MouseKeyboardConfig.default());
      this._gatherIce();
      this._webrtcClient.ontrack = (event) => {
        if (event.track.kind === "video") {
          this._videoComponent = new VideoComponent(this);
          this._videoComponent.create(event.streams[0]);
        } else if (event.track.kind === "audio") {
          this._audioComponent = new AudioComponent(this);
          this._audioComponent.create(event.streams[0]);
        } else {
          console.log("Unknown Track kind: ", event.track.kind);
        }
      };
      this._webrtcClient.addTransceiver("audio", {
        direction: "sendrecv"
      });
      this._webrtcClient.addTransceiver("video", {
        direction: "recvonly"
      });
    }
    createOffer() {
      return new Promise((resolve, reject) => {
        if (this._webrtcClient === void 0) {
          reject("webRTC client not started yet. Run .bind() first.");
          return;
        }
        this.getEventBus().emit("connectionstate", { state: "new" });
        if (this._codecPreference !== "") {
          console.log("xCloudPlayer Library.ts - createOffer() Set codec preference mimetype to:", this._codecPreference);
          this._setCodec(this._codecPreference, this._codecProfiles);
        }
        this._webrtcClient.createOffer({
          offerToReceiveAudio: true,
          offerToReceiveVideo: true
        }).then((offer) => {
          if (this._maxVideoBitrate > 0) {
            console.log("xCloudPlayer Library.ts - createOffer() Set max video bitrate to:", this._maxVideoBitrate, "kbps");
            offer.sdp = this._setBitrate(offer.sdp, "video", this._maxVideoBitrate);
          }
          if (this._maxAudioBitrate > 0) {
            console.log("xCloudPlayer Library.ts - createOffer() Set max audio bitrate to:", this._maxVideoBitrate, "kbps");
            offer.sdp = this._setBitrate(offer.sdp, "audio", this._maxAudioBitrate);
          }
          if ((this._config.sound_force_mono || false) !== true) {
            console.log("xCloudPlayer Library.ts - createOffer() Set audio to stereo");
            offer.sdp = offer.sdp?.replace("useinbandfec=1", "useinbandfec=1; stereo=1");
          }
          this._webrtcClient?.setLocalDescription(offer);
          resolve(offer);
        });
      });
    }
    _sdpHandler;
    sdpNegotiationChat() {
      this.createOffer().then((offer) => {
        this._sdpHandler(this, offer);
      });
    }
    setSdpHandler(listener) {
      this._sdpHandler = listener;
    }
    setAudioBitrate(bitrate_kbps) {
      this._maxAudioBitrate = bitrate_kbps;
    }
    setVideoBitrate(bitrate_kbps) {
      this._maxVideoBitrate = bitrate_kbps;
    }
    setControllerRumble(state) {
      this.getChannelProcessor("input")._rumbleEnabled = state;
    }
    _setBitrate(sdp, media, bitrate) {
      const lines = sdp.split("\n");
      let line = -1;
      for (let i = 0; i < lines.length; i++) {
        if (lines[i].indexOf("m=" + media) === 0) {
          line = i;
          break;
        }
      }
      if (line === -1) {
        console.debug("Could not find the m line for", media);
        return sdp;
      }
      line++;
      while (lines[line].indexOf("i=") === 0 || lines[line].indexOf("c=") === 0) {
        line++;
      }
      if (lines[line].indexOf("b") === 0) {
        lines[line] = "b=AS:" + bitrate;
        return lines.join("\n");
      }
      let newLines = lines.slice(0, line);
      newLines.push("b=AS:" + bitrate);
      newLines = newLines.concat(lines.slice(line, lines.length));
      return newLines.join("\n");
    }
    setCodecPreferences(mimeType, options) {
      this._codecPreference = mimeType;
      if (options) {
        this._codecProfiles = options.profiles;
      }
    }
    _setCodec(mimeType, codecProfiles) {
      const tcvr = this._webrtcClient?.getTransceivers()[1];
      const capabilities = RTCRtpReceiver.getCapabilities("video");
      if (capabilities === null) {
        console.log("xCloudPlayer Library.ts - _setCodec() Failed to get video codecs");
      } else {
        const codecs = capabilities.codecs;
        const prefCodecs = [];
        for (let i = 0; i < codecs.length; i++) {
          if (codecs[i].mimeType === mimeType) {
            if (codecProfiles.length > 0) {
              for (let j = 0; j < codecProfiles.length; j++) {
                if (codecs[i].sdpFmtpLine?.indexOf("profile-level-id=" + codecProfiles[j]) !== -1) {
                  console.log("xCloudPlayer Library.ts - Adding codec as preference:", codecs[i], codecProfiles[j]);
                  prefCodecs.push(codecs[i]);
                }
              }
            } else {
              console.log("xCloudPlayer Library.ts - Adding codec as preference:", codecs[i]);
              prefCodecs.push(codecs[i]);
            }
          }
        }
        if (prefCodecs.length === 0) {
          console.log("xCloudPlayer Library.ts - _setCodec() No video codec matches with mimetype:", mimeType);
        }
        if (tcvr?.setCodecPreferences !== void 0) {
          tcvr?.setCodecPreferences(prefCodecs);
        } else {
          console.log("xCloudPlayer Library.ts - _setCodec() Browser does not support setCodecPreferences()");
        }
      }
    }
    setRemoteOffer(sdpdata) {
      try {
        this._webrtcClient?.setRemoteDescription({
          type: "answer",
          sdp: sdpdata
        });
      } catch (e) {
        console.log("xCloudPlayer Library.ts - setRemoteOffer() Remote SDP is not valid:", sdpdata);
      }
      this.getEventBus().emit("connectionstate", { state: "connecting" });
    }
    reset() {
      if (!this._isResetting) {
        this._isResetting = true;
        this._webrtcClient?.close();
        if (this._audioComponent) {
          this._audioComponent.destroy();
        }
        if (this._videoComponent) {
          this._videoComponent.destroy();
        }
        for (const name in this._webrtcChannelProcessors) {
          this._webrtcChannelProcessors[name].destroy();
        }
        this._inputDriver.stop();
        this._keyboardDriver.stop();
        this._webrtcClient = new RTCPeerConnection(this._webrtcConfiguration);
        this._openDataChannels();
        this._inputDriver.start();
        this._keyboardDriver.start();
        this._gatherIce();
        this._isResetting = false;
      }
    }
    close() {
      if (!this._isResetting) {
        this._isResetting = true;
        this._webrtcClient?.close();
        if (this._audioComponent) {
          this._audioComponent.destroy();
        }
        if (this._videoComponent) {
          this._videoComponent.destroy();
        }
        for (const name in this._webrtcChannelProcessors) {
          this._webrtcChannelProcessors[name].destroy();
        }
        this._inputDriver.stop();
        this._keyboardDriver.stop();
      }
    }
    getIceCandidates() {
      return this._iceCandidates;
    }
    setIceCandidates(iceDetails) {
      for (const candidate in iceDetails) {
        if (iceDetails[candidate].candidate === "a=end-of-candidates") {
          iceDetails[candidate].candidate = "";
        }
        this._webrtcClient?.addIceCandidate({
          candidate: iceDetails[candidate].candidate,
          sdpMid: iceDetails[candidate].sdpMid,
          sdpMLineIndex: iceDetails[candidate].sdpMLineIndex
        });
      }
    }
    getChannel(name) {
      return this._webrtcDataChannels[name];
    }
    _openDataChannels() {
      for (const channel in this._webrtcDataChannelsConfig) {
        this._openDataChannel(channel, this._webrtcDataChannelsConfig[channel]);
      }
    }
    _openDataChannel(name, config) {
      console.log("xCloudPlayer Library.ts - Creating data channel:", name, config);
      this._webrtcDataChannels[name] = this._webrtcClient?.createDataChannel(name, config);
      switch (name) {
        case "input":
          this._webrtcChannelProcessors[name] = new InputChannel("input", this);
          break;
        case "control":
          this._webrtcChannelProcessors[name] = new ControlChannel("control", this);
          break;
        case "chat":
          this._webrtcChannelProcessors[name] = new ChatChannel("chat", this);
          break;
        case "message":
          this._webrtcChannelProcessors[name] = new MessageChannel("message", this);
          break;
      }
      this._webrtcDataChannels[name].addEventListener("open", (event) => {
        if (this._webrtcChannelProcessors[name] !== void 0 && this._webrtcChannelProcessors[name].onOpen !== void 0) {
          this._webrtcChannelProcessors[name].onOpen(event);
        } else {
          console.log("xCloudPlayer Library.ts - [" + name + "] Got open channel:", event);
        }
      });
      this._webrtcDataChannels[name].addEventListener("message", (event) => {
        if (this._webrtcChannelProcessors[name] !== void 0 && this._webrtcChannelProcessors[name].onMessage !== void 0) {
          this._webrtcChannelProcessors[name].onMessage(event);
        } else {
          console.log("xCloudPlayer Library.ts - [" + name + "] Received channel message:", event);
        }
      });
      this._webrtcDataChannels[name].addEventListener("closing", (event) => {
        if (this._webrtcChannelProcessors[name] !== void 0 && this._webrtcChannelProcessors[name].onClosing !== void 0) {
          this._webrtcChannelProcessors[name].onClosing(event);
        } else {
          console.log("xCloudPlayer Library.ts - [" + name + "] Got closing channel:", event);
        }
      });
      this._webrtcDataChannels[name].addEventListener("close", (event) => {
        if (this._webrtcChannelProcessors[name] !== void 0 && this._webrtcChannelProcessors[name].onClose !== void 0) {
          this._webrtcChannelProcessors[name].onClose(event);
        } else {
          console.log("xCloudPlayer Library.ts - [" + name + "] Got close channel:", event);
        }
      });
      this._webrtcDataChannels[name].addEventListener("error", (event) => {
        if (this._webrtcChannelProcessors[name] !== void 0 && this._webrtcChannelProcessors[name].onError !== void 0) {
          this._webrtcChannelProcessors[name].onError(event);
        } else {
          console.log("xCloudPlayer Library.ts - [" + name + "] Got error channel:", event);
        }
      });
      if (name === "input") {
        this._webrtcChannelProcessors[name].addEventListener("state", (event) => {
          this._webrtcStates.streamConnection = event.state;
          this.getEventBus().emit("connectionstate", { state: event.state });
          console.log("xCloudPlayer Library.ts - [" + name + "] Channel state changed to:", event);
        });
      }
    }
    _gatherIce() {
      this._webrtcClient?.addEventListener("icecandidate", (event) => {
        if (event.candidate) {
          console.log("xCloudPlayer Library.ts - ICE candidate found:", event.candidate);
          this._iceCandidates.push(event.candidate);
        }
      });
    }
    getDataChannel(name) {
      return this._webrtcDataChannels[name];
    }
    getChannelProcessor(name) {
      return this._webrtcChannelProcessors[name];
    }
    getEventBus() {
      return this._eventBus;
    }
  };
  return __toCommonJS(Library_exports);
})();
