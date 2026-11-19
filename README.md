# Packwise — 0/1 Knapsack Optimizer

A browser-based interface for the 0/1 knapsack problem from `src/Sac_Dos.java`. Enter each item's name, value, and weight, set the bag capacity, and Packwise finds the most valuable combination without exceeding the limit. Each item can be selected at most once.

The knapsack solver and local web server are implemented in `src/Sac_Dos.java`. `app.js` only validates form inputs, sends them to Java, and displays the returned solution; it does not contain the knapsack algorithm. The server saves the latest setup and solution, plus a history of each run, in a local SQLite database. CPLEX is not required.

## Run locally

Install a JDK, then open a terminal in the project directory and run:

```sh
javac -cp "lib/sqlite-jdbc-3.54.1.0.jar" -d bin src/Sac_Dos.java
java -cp "bin:lib/sqlite-jdbc-3.54.1.0.jar" Sac_Dos
```

Open [http://localhost:8080](http://localhost:8080) in your browser. If port 8080 is already in use, pass another port, for example `java -cp "bin:lib/sqlite-jdbc-3.54.1.0.jar" Sac_Dos 8081`, and open `http://localhost:8081`. Stop the server with **Ctrl+C** in the terminal. The SQLite JDBC driver is in `lib/`; the `sac_dos.db` database is created automatically in the project directory. On Windows, replace the `:` between classpath entries with `;`.

The first launch loads the six sample items from `src/Sac_Dos.java`. Click **Find my best fit** to run the Java solver and save the current items and result. The latest setup is restored from SQLite when the page is reopened. Each successful solve is added to **Solution history**, where you can load a previous run. History starts recording after the first solve with this version; older runs were not kept by the previous database schema. The exact solver supports up to 100 items and reports an error if a particular combination is too complex to finish efficiently.
