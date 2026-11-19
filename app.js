"use strict";

const itemsBody = document.querySelector("#items-body");
const rowTemplate = document.querySelector("#item-row-template");
const itemCount = document.querySelector("#item-count");
const emptyState = document.querySelector("#empty-state");
const capacityInput = document.querySelector("#capacity");
const inputError = document.querySelector("#input-error");
const selectedList = document.querySelector("#selected-list");
const totalValue = document.querySelector("#total-value");
const totalWeight = document.querySelector("#total-weight");
const capacityFill = document.querySelector("#capacity-fill");
const capacityTrack = document.querySelector(".capacity-track");
const capacityCaption = document.querySelector("#capacity-caption");
const solveButton = document.querySelector("#solve-button");
const historyList = document.querySelector("#history-list");
const historyCount = document.querySelector("#history-count");
const historyStatus = document.querySelector("#history-status");
const exampleItems = [
  { name: "x1", value: 4, weight: 12 },
  { name: "x2", value: 2, weight: 2 },
  { name: "x3", value: 1, weight: 1 },
  { name: "x4", value: 10, weight: 4 },
  { name: "x5", value: 2, weight: 1 },
  { name: "x6", value: 2, weight: 3 },
];

function addItem(item = {}) {
  const row = rowTemplate.content.firstElementChild.cloneNode(true);
  row.querySelector(".item-name").value = item.name ?? "";
  row.querySelector(".item-value").value = item.value ?? "";
  row.querySelector(".item-weight").value = item.weight ?? "";
  row.querySelector(".remove-button").addEventListener("click", () => {
    row.remove();
    updateItemCount();
    clearError();
  });
  itemsBody.append(row);
  updateItemCount();
  if (!item.name) row.querySelector(".item-name").focus();
}

function updateItemCount() {
  itemCount.textContent = String(itemsBody.rows.length);
  emptyState.hidden = itemsBody.rows.length !== 0;
}

function clearError() {
  inputError.hidden = true;
  inputError.textContent = "";
}

function showError(message) {
  inputError.textContent = message;
  inputError.hidden = false;
}

function readForm() {
  const rows = [...itemsBody.querySelectorAll(".item-row")];
  if (rows.length === 0) {
    showError("Add at least one item before finding the best fit.");
    return null;
  }

  const capacity = Number(capacityInput.value);
  if (!Number.isFinite(capacity) || capacity <= 0) {
    showError("Enter a capacity greater than zero.");
    capacityInput.focus();
    return null;
  }

  const form = new URLSearchParams({ capacity: String(capacity), count: String(rows.length) });
  for (const [index, row] of rows.entries()) {
    const name = row.querySelector(".item-name").value.trim();
    const value = Number(row.querySelector(".item-value").value);
    const weight = Number(row.querySelector(".item-weight").value);
    if (!name) {
      showError(`Give item ${index + 1} a name.`);
      row.querySelector(".item-name").focus();
      return null;
    }
    if (!Number.isFinite(value) || value < 0) {
      showError(`Enter a valid, non-negative value for “${name}”.`);
      row.querySelector(".item-value").focus();
      return null;
    }
    if (!Number.isFinite(weight) || weight <= 0) {
      showError(`Enter a weight greater than zero for “${name}”.`);
      row.querySelector(".item-weight").focus();
      return null;
    }
    form.set(`item${index}.name`, name);
    form.set(`item${index}.value`, String(value));
    form.set(`item${index}.weight`, String(weight));
  }
  return form;
}

function formatNumber(number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(number);
}

function renderResult(state) {
  totalValue.textContent = formatNumber(state.totalValue);
  totalWeight.textContent = formatNumber(state.totalWeight);
  const usedPercent = Math.min(100, state.totalWeight / state.capacity * 100);
  capacityFill.style.width = `${usedPercent}%`;
  capacityTrack.setAttribute("aria-valuenow", String(Math.round(usedPercent)));
  capacityCaption.textContent = `${formatNumber(usedPercent)}% of your bag’s capacity used`;
  selectedList.replaceChildren();

  const selectedItems = state.items.filter((item) => item.selected);
  if (selectedItems.length === 0) {
    const empty = document.createElement("li");
    empty.className = "selection-placeholder";
    empty.textContent = "No items fit within this capacity.";
    selectedList.append(empty);
    return;
  }

  for (const item of selectedItems) {
    const badge = document.createElement("li");
    badge.textContent = item.name;
    selectedList.append(badge);
  }
}

function renderState(state) {
  capacityInput.value = String(state.capacity);
  itemsBody.replaceChildren();
  for (const item of state.items) addItem(item);
  renderResult(state);
}

async function solve() {
  clearError();
  const form = readForm();
  if (!form) return;

  solveButton.disabled = true;
  try {
    const response = await fetch("/api/solve", {
      method: "POST",
      headers: { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" },
      body: form.toString(),
    });
    const state = await response.json();
    if (!response.ok) throw new Error(state.error || `The server returned HTTP ${response.status}.`);
    renderResult(state);
    await loadHistory();
  } catch (error) {
    showError(`Could not solve or save the problem: ${error.message}`);
  } finally {
    solveButton.disabled = false;
  }
}

function formatHistoryDate(value) {
  const date = new Date(`${value.replace(" ", "T")}Z`);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat(undefined, {
    dateStyle: "medium",
    timeStyle: "short",
  }).format(date);
}

function renderHistory(entries) {
  historyCount.textContent = String(entries.length);
  historyList.replaceChildren();
  historyStatus.hidden = entries.length !== 0;
  historyStatus.textContent = entries.length === 0
    ? "No saved runs yet. Solve a problem to start your history."
    : "";

  for (const entry of entries) {
    const card = document.createElement("li");
    card.className = "history-card";

    const date = document.createElement("p");
    date.className = "history-date";
    date.textContent = formatHistoryDate(entry.createdAt);

    const metrics = document.createElement("div");
    metrics.className = "history-metrics";
    for (const [label, value] of [
      ["Value", formatNumber(entry.totalValue)],
      ["Weight", `${formatNumber(entry.totalWeight)} kg`],
      ["Capacity", `${formatNumber(entry.capacity)} kg`],
    ]) {
      const metric = document.createElement("span");
      metric.append(`${label}: `);
      const strong = document.createElement("strong");
      strong.textContent = value;
      metric.append(strong);
      metrics.append(metric);
    }

    const picks = document.createElement("p");
    picks.className = "history-picks";
    const selectedNames = entry.items
      .filter((item) => item.selected)
      .map((item) => item.name);
    picks.textContent = selectedNames.length
      ? `Packed: ${selectedNames.join(", ")}`
      : "No items selected";

    const loadButton = document.createElement("button");
    loadButton.type = "button";
    loadButton.className = "history-load";
    loadButton.textContent = "Load this run";
    loadButton.addEventListener("click", () => {
      renderState(entry);
      historyStatus.hidden = false;
      historyStatus.textContent = "Previous run loaded. Click “Find my best fit” to save a new run.";
    });

    card.append(date, metrics, picks, loadButton);
    historyList.append(card);
  }
}

async function loadHistory() {
  try {
    const response = await fetch("/api/history");
    const entries = await response.json();
    if (!response.ok) throw new Error(entries.error || `The server returned HTTP ${response.status}.`);
    renderHistory(entries);
  } catch (error) {
    historyStatus.hidden = false;
    historyStatus.textContent = `Could not load solution history: ${error.message}`;
  }
}

document.querySelector("#add-item").addEventListener("click", () => addItem());
solveButton.addEventListener("click", solve);
document.querySelector("#history-refresh").addEventListener("click", loadHistory);
document.querySelector("#load-example").addEventListener("click", () => {
  itemsBody.replaceChildren();
  for (const item of exampleItems) addItem(item);
  capacityInput.value = "15";
  clearError();
  solve();
});

itemsBody.addEventListener("input", clearError);
capacityInput.addEventListener("input", clearError);

async function loadSavedState() {
  try {
    const response = await fetch("/api/state");
    const state = await response.json();
    if (!response.ok) throw new Error(state.error || `The server returned HTTP ${response.status}.`);
    renderState(state);
  } catch (error) {
    showError(`Could not load saved data from SQLite: ${error.message}`);
    totalValue.textContent = "—";
    totalWeight.textContent = "—";
    capacityCaption.textContent = "Start the Java server and reload this page.";
  }
}

loadSavedState();
loadHistory();
