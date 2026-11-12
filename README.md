# Packwise — 0/1 Knapsack Optimizer

A browser-based interface for the 0/1 knapsack problem from `src/Sac_Dos.java`. Enter each item's name, value, and weight, set the bag capacity, and Packwise finds the most valuable combination without exceeding the limit. Each item can be selected at most once.

The optimizer runs entirely in your browser. Item details are not sent to a server, and the site does not require Java, CPLEX, or a backend.

## Run locally

Open `index.html` in a browser. To try the values from the Java example, choose **Load example**.

## Publish on Netlify

### Deploy from a Git repository

1. Push the project to a GitHub or GitLab repository.
2. In Netlify, choose **Add new site → Import an existing project** and connect that repository.
3. Leave the build command and publish directory blank; the site files (`index.html`, `styles.css`, and `app.js`) are at the project root.
4. Choose **Deploy site**. Netlify will provide a public `netlify.app` address and redeploy when you push changes.

### Deploy without Git

In the Netlify dashboard, choose **Add new site → Deploy manually** and drag the project folder into the drop area. For updates, upload the folder again.

The optimizer runs in the visitor's browser. Entered item details are not uploaded or stored by the site.
