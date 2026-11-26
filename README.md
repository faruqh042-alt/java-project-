# Pet Adoption Center Manager

A complete Java 17 desktop application for managing pets, adopters, adoption requests and adoption records.

## Requirements

- Java JDK 17 or newer
- Visual Studio Code
- Windows, macOS or Linux

## Run in VS Code

1. Open this project folder in VS Code.
2. Open `src/Main.java`.
3. Use **Run Java** or press **F5**.

## Run with npm convenience scripts

```powershell
npm install
npm run dev
```

Use `npm start` for the same desktop application. The project is Java Swing and does not use a browser server or localhost URL.

## Run the web interface locally

The web interface shares the existing CSV data and management logic with the desktop app. Set credentials before starting it:

```powershell
$env:APP_USERNAME = "admin"
$env:APP_PASSWORD = "choose-a-strong-password"
npm run web
```

Open `http://localhost:8080`. On macOS/Linux, export `APP_USERNAME` and `APP_PASSWORD` before running `npm run web`.

## Run from a terminal

```powershell
javac -encoding UTF-8 -d out (Get-ChildItem src/*.java).FullName
java -cp out Main
```

On Windows PowerShell, run both commands separately. On macOS/Linux, compile with:

```sh
javac -encoding UTF-8 -d out src/*.java
java -cp out Main
```

## Data

The application stores its records in:

- `data/pets.csv`
- `data/adopters.csv`
- `data/adoption_requests.csv`
- `data/adoption_records.csv`

Sample records are created once, only when the CSV files are empty or missing. Existing user data is never replaced by sample data.

The interface uses the locally bundled Nunito font files in `assets/fonts/`, so typography works offline.

## Main Features

- Dashboard statistics and adoption overview
- Pet registration, editing, deletion, search and filters
- Adopter registration and management
- Adoption request submission, approval and rejection
- Adoption history and duplicate-adoption prevention
- Reports, custom Swing charts and CSV export
- CSV-safe persistence with validation and confirmation dialogs

## Deploy the web interface to Render

The repository includes a Dockerfile and Render Blueprint (`render.yaml`).

1. Push this repository to GitHub.
2. In Render, choose **New → Blueprint** and connect this repository.
3. Set `APP_USERNAME` and a strong `APP_PASSWORD` when prompted. These protect the management interface.
4. Create the service. Render will build the Java web app and provision a persistent disk for the CSV database.

The persistent disk requires a paid Render instance. Do not remove it unless you are comfortable losing changes made to the CSV records when the service is redeployed. The original Swing app remains available with `npm run dev`.
