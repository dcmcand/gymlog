import Poco from "commodetto/Poco";
import Message from "pebble/message";
import Button from "pebble/button";
import Vibes from "pebble/vibes";

const render = new Poco(screen);
const black = render.makeColor(0, 0, 0);
const white = render.makeColor(255, 255, 255);

// Exact (family, size) pairs the Pebble Poco font table supports.
const exFont = new render.Font("Gothic-Bold", 28);
const smallFont = new render.Font("Gothic-Regular", 24);
const bigFont = new render.Font("Bitham-Bold", 42);
const hintFont = new render.Font("Gothic-Regular", 14);

const EXTEND_SECONDS = 90;

// Populated from the phone (keys pinned to the WatchProtocol integers).
let exerciseName = "";
let targetText = "";
let setLabel = "";
let remaining = 0;   // seconds left in the current rest
let running = false; // is a rest timer counting down

function two(n) {
	return n < 10 ? "0" + n : "" + n;
}

function formatTime(sec) {
	const m = Math.floor(sec / 60);
	const s = sec % 60;
	return m + ":" + two(s);
}

function drawCentered(text, font, y) {
	if (!text) return;
	const w = render.getTextWidth(text, font);
	render.drawText(text, font, black, (render.width - w) / 2, y);
}

function draw() {
	render.begin();
	render.fillRectangle(white, 0, 0, render.width, render.height);

	if (exerciseName === "") {
		// No pending set: workout complete / idle.
		drawCentered("All done", exFont, (render.height - exFont.height) / 2);
	} else {
		drawCentered(exerciseName, exFont, 4);
		drawCentered(targetText, smallFont, 44);
		drawCentered(setLabel, smallFont, 76);
		if (running) {
			drawCentered(formatTime(remaining), bigFont, 112);
		} else {
			drawCentered("Ready", bigFont, 112);
		}
		drawCentered("Easy / +Time / Hard", hintFont, render.height - 22);
	}

	render.end();
}

// Tick the local countdown once per second; the phone's timer is the source of truth and
// re-syncs us on every start/extend/stop via onReadable.
watch.addEventListener("secondchange", function () {
	if (running && remaining > 0) {
		remaining -= 1;
		if (remaining <= 0) {
			remaining = 0;
			running = false;
			Vibes.doublePulse(); // buzz when the rest is over
		}
		draw();
	}
});

const message = new Message({
	keys: new Map([
		["EXERCISE", 0],
		["TARGET", 1],
		["SETLABEL", 2],
		["DURATION", 3],
		["RUNNING", 4],
		["CMD", 10],
	]),
	onReadable() {
		const m = this.read();
		if (m.has("EXERCISE")) exerciseName = m.get("EXERCISE");
		if (m.has("TARGET")) targetText = m.get("TARGET");
		if (m.has("SETLABEL")) setLabel = m.get("SETLABEL");
		if (m.has("DURATION")) remaining = m.get("DURATION");
		if (m.has("RUNNING")) running = m.get("RUNNING") === 1;
		draw();
	},
});

// Send a command to the phone. Valid once the phone has sent at least one message (the
// outbox becomes writable after the first inbound); GymLog always pushes context first.
function send(cmd) {
	try {
		message.write(new Map([["CMD", cmd]]));
	} catch (e) {
		console.log("send failed: " + e);
	}
}

new Button({
	types: ["up"],
	onPush(down) {
		if (down) send(1); // Easy
	},
});

new Button({
	types: ["select"],
	onPush(down) {
		if (!down) return;
		if (!running) return; // only extend an active rest (matches the phone's no-op)
		// Extend the rest: bump the local countdown immediately and tell the phone too.
		remaining += EXTEND_SECONDS;
		draw();
		send(3); // extend rest
	},
});

new Button({
	types: ["down"],
	onPush(down) {
		if (down) send(2); // Hard
	},
});

draw();
