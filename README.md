# Packwise — 0/1 Knapsack Optimizer

A browser-based interface for the 0/1 knapsack problem from `src/Sac_Dos.java`. Enter each item's name, value, and weight, set the bag capacity, and Packwise finds the most valuable combination without exceeding the limit. Each item can be selected at most once.

The optimizer runs entirely in your browser. Item details are not sent to a server, and the site does not require Java, CPLEX, or a backend.

## Run locally

Open `index.html` in a browser. To try the values from the Java example, choose **Load example**.

## Publish on GitHub Pages

1. Create a GitHub repository and push these project files to its `main` branch.
2. In the repository, open **Settings → Pages** and select **GitHub Actions** as the build and deployment source.
3. The included workflow deploys the site on every push to `main`. Once it completes, open the Pages URL shown in the workflow run or under **Settings → Pages**.

GitHub Pages is suitable for this static, browser-only app. A public repository can be published at no cost; hosting the site does not upload or store the values entered into the app.
