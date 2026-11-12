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

const exampleItems = [
  { name: "x1", value: 4, weight: 12 },
  { name: "x2", value: 2, weight: 2 },
  { name: "x3", value: 1, weight: 1 },
  { name: "x4", value: 10, weight: 4 },
  { name: "x5", value: 2, weight: 1 },
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
  const count = itemsBody.rows.length;
  itemCount.textContent = String(count);
  emptyState.hidden = count !== 0;
}

function clearError() {
  inputError.hidden = true;
  inputError.textContent = "";
}

function showError(message) {
  inputError.textContent = message;
  inputError.hidden = false;
}

function readItems() {
  const rows = [...itemsBody.querySelectorAll(".item-row")];
  if (rows.length === 0) {
    showError("Add at least one item before finding the best fit.");
    return null;
  }

  const items = [];
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
    items.push({ name, value, weight, originalIndex: index });
  }

  const capacity = Number(capacityInput.value);
  if (!Number.isFinite(capacity) || capacity <= 0) {
    showError("Enter a capacity greater than zero.");
    capacityInput.focus();
    return null;
  }
  return { items, capacity };
}

function optimizeKnapsack(items, capacity) {
  let frontier = [{ weight: 0, value: 0, picked: [] }];

  for (const item of items) {
    const candidates = frontier.slice();
    for (const state of frontier) {
      const weight = state.weight + item.weight;
      if (weight <= capacity) {
        candidates.push({
          weight,
          value: state.value + item.value,
          picked: [...state.picked, item.originalIndex],
        });
      }
    }

    candidates.sort((a, b) => a.weight - b.weight || b.value - a.value);
    const nextFrontier = [];
    let bestValue = -Infinity;
    for (const state of candidates) {
      if (state.value > bestValue) {
        nextFrontier.push(state);
        bestValue = state.value;
      }
    }
    frontier = nextFrontier;
  }

  return frontier.reduce((best, state) => state.value > best.value ? state : best, frontier[0]);
}

function formatNumber(number) {
  return new Intl.NumberFormat(undefined, { maximumFractionDigits: 2 }).format(number);
}

function solve() {
  clearError();
  const data = readItems();
  if (!data) return;

  const result = optimizeKnapsack(data.items, data.capacity);
  const pickedItems = new Set(result.picked);
  const usedPercent = Math.min(100, result.weight / data.capacity * 100);

  totalValue.textContent = formatNumber(result.value);
  totalWeight.textContent = formatNumber(result.weight);
  capacityFill.style.width = `${usedPercent}%`;
  capacityTrack.setAttribute("aria-valuenow", String(Math.round(usedPercent)));
  capacityCaption.textContent = `${formatNumber(usedPercent)}% of your bag’s capacity used`;
  selectedList.replaceChildren();

  if (pickedItems.size === 0) {
    const empty = document.createElement("li");
    empty.className = "selection-placeholder";
    empty.textContent = "No items fit within this capacity.";
    selectedList.append(empty);
    return;
  }

  for (const item of data.items) {
    if (!pickedItems.has(item.originalIndex)) continue;
    const badge = document.createElement("li");
    badge.textContent = item.name;
    selectedList.append(badge);
  }
}

document.querySelector("#add-item").addEventListener("click", () => addItem());
document.querySelector("#solve-button").addEventListener("click", solve);
document.querySelector("#load-example").addEventListener("click", () => {
  itemsBody.replaceChildren();
  for (const item of exampleItems) addItem(item);
  capacityInput.value = "15";
  clearError();
  solve();
});

itemsBody.addEventListener("input", clearError);
capacityInput.addEventListener("input", clearError);

for (const item of exampleItems) addItem(item);
solve();
