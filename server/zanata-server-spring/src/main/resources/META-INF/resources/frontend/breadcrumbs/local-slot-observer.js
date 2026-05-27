/*
 * SlotObserver shim for Vaadin 25.2-alpha5.
 *
 * The breadcrumbs source on the main branch calls
 * `new SlotObserver(this.shadowRoot, callback)` — a newer API that listens
 * to all <slot> children of a root and fires when any of them get nodes
 * added/removed (also fires once on connect for the initial population).
 *
 * The installed alpha5 SlotObserver still uses the older single-slot
 * signature `new SlotObserver(slotElement, callback)` and calls
 * `this.slot.assignedNodes()` — which fails when passed a ShadowRoot.
 *
 * This shim re-implements just the surface the vendored breadcrumbs
 * source uses: a `flush()` method that runs the callback once, and a
 * `slotchange` listener on every <slot> in the root. Drop this file
 * along with the vendored breadcrumbs source when Vaadin ships a
 * working npm release.
 */
export class SlotObserver {
  constructor(root, callback) {
    this.root = root;
    this.callback = callback;
    this._onSlotChange = () => this.callback();
    // Listen to slotchange on every existing slot…
    this._slots = new Set();
    this._attachExisting();
    // …and to any slot added later, in case render() inserts more slots.
    this._mutationObserver = new MutationObserver(() => this._attachExisting());
    this._mutationObserver.observe(root, { childList: true, subtree: true });
  }

  _attachExisting() {
    this.root.querySelectorAll('slot').forEach((slot) => {
      if (!this._slots.has(slot)) {
        this._slots.add(slot);
        slot.addEventListener('slotchange', this._onSlotChange);
      }
    });
  }

  flush() {
    this.callback();
  }

  disconnect() {
    this._mutationObserver.disconnect();
    this._slots.forEach((slot) => slot.removeEventListener('slotchange', this._onSlotChange));
    this._slots.clear();
  }
}
