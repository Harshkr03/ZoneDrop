const AUTH_STORAGE_KEY = "zonedrop.auth";

const authStore = {
  read() {
    try {
      const raw = window.localStorage.getItem(AUTH_STORAGE_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch (_error) {
      return null;
    }
  },

  save(payload) {
    window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(payload));
  },

  clear() {
    window.localStorage.removeItem(AUTH_STORAGE_KEY);
  },

  getToken() {
    return this.read()?.token || "";
  },

  getUser() {
    return this.read()?.user || null;
  },

  isAuthenticated() {
    return Boolean(this.getToken());
  }
};

const api = {
  async request(path, options = {}) {
    const headers = new Headers(options.headers || {});
    const token = authStore.getToken();

    if (token) {
      headers.set("Authorization", `Bearer ${token}`);
    }

    const response = await fetch(path, {
      ...options,
      headers
    });

    if (response.status === 401 || response.status === 403) {
      authStore.clear();
      if (document.body.dataset.page !== "auth") {
        window.location.href = "/";
      }
    }

    if (!response.ok) {
      let message = "Request failed";
      try {
        const payload = await response.json();
        message = payload.message || payload.error || JSON.stringify(payload);
      } catch (_error) {
        message = await response.text() || message;
      }
      throw new Error(message);
    }

    if (response.status === 204) {
      return null;
    }

    return response.json();
  },

  signup(payload) {
    return this.request("/auth/signup", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },

  login(payload) {
    return this.request("/auth/login", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },

  getUsers() {
    return this.request("/zonedrop/users");
  },

  getUsersWithLocations() {
    return this.request("/zonedrop/users/locations");
  },

  createUser(payload) {
    return this.request("/zonedrop/users", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },

  updateLiveLocation(payload) {
    return this.request("/zonedrop/users/live-location", {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });
  },

  uploadFile(userId, file) {
    const formData = new FormData();
    formData.append("userId", userId);
    formData.append("file", file);

    return this.request("/zonedrop/files", {
      method: "POST",
      body: formData
    });
  },

  getAllFiles() {
    return this.request("/zonedrop/files");
  },

  getFilesByUser(userId) {
    return this.request(`/zonedrop/files/${userId}`);
  },

  findNearbyUsers(latitude, longitude, radiusKm) {
    const params = new URLSearchParams({ latitude, longitude, radiusKm });
    return this.request(`/zonedrop/users/nearby?${params.toString()}`);
  }
};

function setStatus(element, message, type = "") {
  if (!element) {
    return;
  }
  element.textContent = message || "";
  element.className = type ? `status ${type}` : "status";
}

function formatBytes(bytes) {
  if (bytes == null) {
    return "Unknown size";
  }
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`;
}

function formatDistance(distanceKm) {
  if (distanceKm == null) {
    return "distance unavailable";
  }
  return `${distanceKm.toFixed(2)} km`;
}

function requireAuth() {
  if (!authStore.isAuthenticated()) {
    window.location.href = "/";
    return false;
  }
  return true;
}

function hydrateAuthChrome() {
  const user = authStore.getUser();
  const pill = document.querySelector("#auth-user-pill");
  if (pill && user) {
    pill.textContent = `${user.name} · ${user.email}`;
  }

  const logoutButton = document.querySelector("#logout-button");
  if (logoutButton) {
    logoutButton.addEventListener("click", () => {
      authStore.clear();
      window.location.href = "/";
    });
  }
}

function persistAuth(authResponse) {
  authStore.save({
    token: authResponse.token,
    user: authResponse.user
  });
}

function switchAuthMode(mode) {
  const loginForm = document.querySelector("#login-form");
  const signupForm = document.querySelector("#signup-form");
  const loginTab = document.querySelector("#login-tab");
  const signupTab = document.querySelector("#signup-tab");

  const isLogin = mode === "login";
  loginForm.classList.toggle("hidden", !isLogin);
  signupForm.classList.toggle("hidden", isLogin);
  loginTab.classList.toggle("active", isLogin);
  signupTab.classList.toggle("active", !isLogin);
}

function initAuthPage() {
  if (authStore.isAuthenticated()) {
    window.location.href = "/dashboard.html";
    return;
  }

  const loginForm = document.querySelector("#login-form");
  const signupForm = document.querySelector("#signup-form");
  const loginStatus = document.querySelector("#login-status");
  const signupStatus = document.querySelector("#signup-status");

  document.querySelectorAll(".auth-tab").forEach((tab) => {
    tab.addEventListener("click", () => switchAuthMode(tab.dataset.mode));
  });

  loginForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus(loginStatus, "Signing in...");
    const formData = new FormData(loginForm);

    try {
      const authResponse = await api.login({
        email: formData.get("email"),
        password: formData.get("password")
      });
      persistAuth(authResponse);
      setStatus(loginStatus, "Login successful.", "success");
      window.location.href = "/dashboard.html";
    } catch (error) {
      setStatus(loginStatus, error.message, "error");
    }
  });

  signupForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus(signupStatus, "Creating account...");
    const formData = new FormData(signupForm);

    try {
      const authResponse = await api.signup({
        name: formData.get("name"),
        email: formData.get("email"),
        password: formData.get("password")
      });
      persistAuth(authResponse);
      setStatus(signupStatus, "Account created.", "success");
      window.location.href = "/dashboard.html";
    } catch (error) {
      setStatus(signupStatus, error.message, "error");
    }
  });
}

async function initUsersPage() {
  if (!requireAuth()) {
    return;
  }
  hydrateAuthChrome();

  const createForm = document.querySelector("#create-user-form");
  const locationForm = document.querySelector("#update-location-form");
  const createStatus = document.querySelector("#create-user-status");
  const locationStatus = document.querySelector("#update-location-status");
  const usersList = document.querySelector("#users-list");
  const locationUserSelect = document.querySelector("#location-user-id");

  async function refreshUsers() {
    const users = await api.getUsersWithLocations();

    locationUserSelect.innerHTML = users.length
      ? users.map((user) => `<option value="${user.userId}">${user.userId} · ${user.name}</option>`).join("")
      : '<option value="">No users yet</option>';

    usersList.innerHTML = users.length
      ? users.map((user) => `
          <article class="card">
            <h3>${user.name}</h3>
            <div class="meta">
              <span>User ID: ${user.userId}</span>
              <span>${user.email}</span>
              <span>${user.latitude == null ? "Location not set" : `Lat ${user.latitude}, Lng ${user.longitude}`}</span>
            </div>
          </article>
        `).join("")
      : '<div class="empty">Create a user to start the flow.</div>';
  }

  createForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus(createStatus, "Creating user...");
    const formData = new FormData(createForm);

    try {
      const created = await api.createUser({
        name: formData.get("name"),
        email: formData.get("email"),
        password: formData.get("password")
      });
      createForm.reset();
      await refreshUsers();
      setStatus(createStatus, `Created ${created.name} with id ${created.id}.`, "success");
    } catch (error) {
      setStatus(createStatus, error.message, "error");
    }
  });

  locationForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus(locationStatus, "Updating location...");
    const formData = new FormData(locationForm);

    try {
      await api.updateLiveLocation({
        userId: Number(formData.get("userId")),
        latitude: Number(formData.get("latitude")),
        longitude: Number(formData.get("longitude"))
      });
      await refreshUsers();
      setStatus(locationStatus, "Location saved to Postgres and Redis.", "success");
    } catch (error) {
      setStatus(locationStatus, error.message, "error");
    }
  });

  try {
    await refreshUsers();
  } catch (error) {
    usersList.innerHTML = `<div class="empty">${error.message}</div>`;
    setStatus(createStatus, error.message, "error");
  }
}

async function initUploadPage() {
  if (!requireAuth()) {
    return;
  }
  hydrateAuthChrome();

  const uploadForm = document.querySelector("#upload-form");
  const uploadStatus = document.querySelector("#upload-status");
  const uploadUserSelect = document.querySelector("#upload-user-id");
  const filesList = document.querySelector("#all-files-list");

  async function refreshUsers() {
    const users = await api.getUsers();
    uploadUserSelect.innerHTML = users.length
      ? users.map((user) => `<option value="${user.id}">${user.id} · ${user.name}</option>`).join("")
      : '<option value="">No users available</option>';
  }

  async function refreshFiles() {
    const files = await api.getAllFiles();
    filesList.innerHTML = files.length
      ? files.map((file) => `
          <article class="card">
            <h3>${file.fileName}</h3>
            <div class="meta">
              <span>User: ${file.userName} (#${file.userId})</span>
              <span>${formatBytes(file.fileSize)}</span>
            </div>
            <div class="actions">
              <a class="button secondary" href="${file.downloadUrl}" target="_blank" rel="noreferrer">Download</a>
            </div>
          </article>
        `).join("")
      : '<div class="empty">No files uploaded yet.</div>';
  }

  uploadForm.addEventListener("submit", async (event) => {
    event.preventDefault();
    setStatus(uploadStatus, "Uploading file...");
    const fileInput = uploadForm.querySelector('input[name="file"]');
    const selectedFile = fileInput.files[0];

    if (!selectedFile) {
      setStatus(uploadStatus, "Choose a file first.", "error");
      return;
    }

    try {
      await api.uploadFile(uploadUserSelect.value, selectedFile);
      uploadForm.reset();
      await Promise.all([refreshUsers(), refreshFiles()]);
      setStatus(uploadStatus, "Upload complete.", "success");
    } catch (error) {
      setStatus(uploadStatus, error.message, "error");
    }
  });

  try {
    await Promise.all([refreshUsers(), refreshFiles()]);
  } catch (error) {
    filesList.innerHTML = `<div class="empty">${error.message}</div>`;
    setStatus(uploadStatus, error.message, "error");
  }
}

async function initNearbyPage() {
  if (!requireAuth()) {
    return;
  }
  hydrateAuthChrome();

  const form = document.querySelector("#nearby-form");
  const status = document.querySelector("#nearby-status");
  const userSelect = document.querySelector("#nearby-user-id");
  const radiusInput = document.querySelector("#radius-km");
  const info = document.querySelector("#selected-user-info");
  const results = document.querySelector("#nearby-results");

  let users = [];
  let map;
  let markersLayer;
  let radiusLayer;

  function ensureMap() {
    if (map) {
      return;
    }

    map = L.map("nearby-map").setView([12.9716, 77.5946], 12);
    L.tileLayer("https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png", {
      attribution: "&copy; OpenStreetMap contributors"
    }).addTo(map);

    markersLayer = L.layerGroup().addTo(map);
  }

  function renderMap(originUser, nearbyUsersWithFiles, radiusKm) {
    ensureMap();
    markersLayer.clearLayers();

    if (radiusLayer) {
      radiusLayer.remove();
      radiusLayer = null;
    }

    const originLatLng = [originUser.latitude, originUser.longitude];
    map.setView(originLatLng, 13);

    radiusLayer = L.circle(originLatLng, {
      radius: Number(radiusKm) * 1000,
      color: "#67d8ff",
      fillColor: "#67d8ff",
      fillOpacity: 0.12
    }).addTo(map);

    L.marker(originLatLng)
      .addTo(markersLayer)
      .bindPopup(`<strong>${originUser.name}</strong><br>Search origin`);

    nearbyUsersWithFiles.forEach((user) => {
      if (user.latitude == null || user.longitude == null) {
        return;
      }

      const fileLinks = user.files.length
        ? user.files.map((file) => `
            <div style="margin-top:8px;">
              <a href="${file.downloadUrl}" target="_blank" rel="noreferrer">${file.fileName}</a>
            </div>
          `).join("")
        : '<div style="margin-top:8px;">No files uploaded</div>';

      L.circleMarker([user.latitude, user.longitude], {
        radius: 9,
        color: "#79f0d1",
        fillColor: "#79f0d1",
        fillOpacity: 0.85
      })
        .addTo(markersLayer)
        .bindPopup(`
          <strong>${user.name}</strong><br>
          ${formatDistance(user.distanceKm)} away
          ${fileLinks}
        `);
    });
  }

  function syncSelectedUser() {
    const selectedUser = users.find((user) => String(user.userId) === userSelect.value);
    info.innerHTML = selectedUser
      ? `
          <div class="meta">
            <span>${selectedUser.email}</span>
            <span>Lat ${selectedUser.latitude}</span>
            <span>Lng ${selectedUser.longitude}</span>
          </div>
        `
      : '<div class="empty">Select a user with a saved live location.</div>';
  }

  async function refreshUsers() {
    const allUsers = await api.getUsersWithLocations();
    users = allUsers.filter((user) => user.latitude != null && user.longitude != null);

    userSelect.innerHTML = users.length
      ? users.map((user) => `<option value="${user.userId}">${user.name}</option>`).join("")
      : '<option value="">No users with live location</option>';

    syncSelectedUser();
  }

  userSelect.addEventListener("change", syncSelectedUser);

  form.addEventListener("submit", async (event) => {
    event.preventDefault();
    const selectedUser = users.find((user) => String(user.userId) === userSelect.value);

    if (!selectedUser) {
      setStatus(status, "Choose a user with a live location first.", "error");
      results.innerHTML = '<div class="empty">No searchable user is available yet.</div>';
      return;
    }

    setStatus(status, "Searching nearby users...");

    try {
      const nearbyUsers = await api.findNearbyUsers(
        selectedUser.latitude,
        selectedUser.longitude,
        radiusInput.value
      );

      const usersWithFiles = await Promise.all(
        nearbyUsers.map(async (user) => ({
          ...user,
          files: await api.getFilesByUser(user.userId)
        }))
      );

      results.innerHTML = usersWithFiles.length
        ? usersWithFiles.map((user) => `
            <article class="card">
              <h3>${user.name}</h3>
              <div class="meta">
                <span>${user.email}</span>
                <span>${formatDistance(user.distanceKm)}</span>
                <span>Lat ${user.latitude}</span>
                <span>Lng ${user.longitude}</span>
              </div>
              <div class="files">
                ${user.files.length
                  ? user.files.map((file) => `
                      <a class="file-chip" href="${file.downloadUrl}" target="_blank" rel="noreferrer">
                        ${file.fileName} · ${formatBytes(file.fileSize)}
                      </a>
                    `).join("")
                  : '<div class="empty">No files uploaded by this user.</div>'
                }
              </div>
            </article>
          `).join("")
        : '<div class="empty">No nearby users found. Update locations first if Redis is empty.</div>';

      renderMap(selectedUser, usersWithFiles, radiusInput.value);
      setStatus(status, "Nearby users loaded on the map.", "success");
    } catch (error) {
      setStatus(status, error.message, "error");
      results.innerHTML = `<div class="empty">${error.message}</div>`;
    }
  });

  try {
    await refreshUsers();
    ensureMap();
  } catch (error) {
    results.innerHTML = `<div class="empty">${error.message}</div>`;
    setStatus(status, error.message, "error");
  }
}

function initDashboardPage() {
  if (!requireAuth()) {
    return;
  }
  hydrateAuthChrome();
}

const page = document.body.dataset.page;

if (page === "auth") {
  initAuthPage();
}

if (page === "dashboard") {
  initDashboardPage();
}

if (page === "users") {
  initUsersPage();
}

if (page === "upload") {
  initUploadPage();
}

if (page === "nearby") {
  initNearbyPage();
}
