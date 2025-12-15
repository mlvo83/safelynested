// Common utilities for Shelter Platform

const API_BASE_URL = 'http://localhost:8080/api';
// Login function - updated to store charity partner info
async function login(username, password) {
    try {
        const response = await fetch(`${API_BASE_URL}/auth/login`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ username, password })
        });

        if (!response.ok) {
            const error = await response.json();
            throw new Error(error || 'Login failed');
        }

        const data = await response.json();

        // Store token (both localStorage and cookie)
        storeToken(data.token);

        // Store user info (including charity partner if present)
        const userInfo = {
            id: data.userId,
            username: data.username,
            email: data.email,
            role: data.role
        };

        // ⭐ Include charity partner info if present
        if (data.charityPartnerId) {
            userInfo.charityPartnerId = data.charityPartnerId;
            userInfo.charityPartnerName = data.charityPartnerName;
        }

        setUserInfo(userInfo);

        return data;
    } catch (error) {
        console.error('Login error:', error);
        throw error;
    }
}
// Get token from localStorage
function getToken() {
    return localStorage.getItem('token');
}

// Set token in localStorage
function setToken(token) {
    localStorage.setItem('token', token);
}

// Get user info from localStorage
function getUserInfo() {
    const userInfo = localStorage.getItem('userInfo');
    return userInfo ? JSON.parse(userInfo) : null;
}

// Set user info in localStorage
function setUserInfo(userInfo) {
    localStorage.setItem('userInfo', JSON.stringify(userInfo));
}

// Clear auth data
function clearAuth() {
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');
}

// Check if user is authenticated
function isAuthenticated() {
    return getToken() !== null;
}

// Logout function
function logout() {
    clearAuth();
    window.location.href = '/login.html';
}

// Make authenticated API call
async function apiCall(endpoint, options = {}) {
    const token = getToken();
    const headers = {
        'Content-Type': 'application/json',
        ...options.headers
    };

    if (token) {
        headers['Authorization'] = `Bearer ${token}`;
    }

    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            ...options,
            headers
        });

        if (response.status === 401) {
            clearAuth();
            window.location.href = '/login.html';
            throw new Error('Unauthorized');
        }

        if (!response.ok) {
            const error = await response.text();
            throw new Error(error || 'Request failed');
        }

        const contentType = response.headers.get('content-type');
        if (contentType && contentType.includes('application/json')) {
            return await response.json();
        }
        return await response.text();
    } catch (error) {
        console.error('API call error:', error);
        throw error;
    }
}

// Show alert message
function showAlert(message, type = 'success') {
    const alertDiv = document.createElement('div');
    alertDiv.className = `alert alert-${type}`;
    alertDiv.textContent = message;

    const container = document.querySelector('.container');
    if (container) {
        container.insertBefore(alertDiv, container.firstChild);
        setTimeout(() => alertDiv.remove(), 5000);
    }
}

// Format date
function formatDate(dateString) {
    if (!dateString) return 'N/A';
    const date = new Date(dateString);
    return date.toLocaleDateString() + ' ' + date.toLocaleTimeString();
}

// Format status for display
function getStatusBadge(status) {
    const statusMap = {
        'DRAFT': 'badge-draft',
        'SUBMITTED': 'badge-submitted',
        'INVITE_SENT': 'badge-invite-sent',
        'CLIENT_VIEWING': 'badge-invite-sent',
        'LOCATION_SELECTED': 'badge-location-selected',
        'BOOKING_IN_PROGRESS': 'badge-location-selected',
        'BOOKED': 'badge-booked',
        'COMPLETED': 'badge-completed'
    };

    const statusLabel = status.replace(/_/g, ' ');
    const badgeClass = statusMap[status] || 'badge-draft';

    return `<span class="badge ${badgeClass}">${statusLabel}</span>`;
}

// Show loading spinner
function showLoading(element) {
    element.innerHTML = '<div class="spinner"></div>';
}

// Hide element
function hideElement(element) {
    if (element) {
        element.classList.add('hidden');
    }
}

// Show element
function showElement(element) {
    if (element) {
        element.classList.remove('hidden');
    }
}

// Check auth on page load for protected pages
function requireAuth() {
    if (!isAuthenticated()) {
        window.location.href = '/login.html';
    }
}

// Check if user has required role
function checkRole(requiredRole) {
    const userInfo = getUserInfo();
    if (!userInfo || userInfo.role !== requiredRole) {
        showAlert('Access denied', 'danger');
        setTimeout(() => window.location.href = '/login.html', 2000);
        return false;
    }
    return true;
}


// In common.js - update the login/storeToken function

function storeToken(token) {
    // Store in localStorage (for API calls)
    localStorage.setItem('token', token);

    // Store in cookie (for HTML page navigation)
    document.cookie = `token=${token}; path=/; max-age=86400; SameSite=Strict`;
}

function logout() {
    // Clear localStorage
    localStorage.removeItem('token');
    localStorage.removeItem('userInfo');

    // Clear cookie
    document.cookie = 'token=; path=/; max-age=0';

    // Redirect to login
    window.location.href = '/login.html';
}

function getToken() {
    // Try localStorage first
    let token = localStorage.getItem('token');

    // If not in localStorage, try cookie
    if (!token) {
        const cookies = document.cookie.split(';');
        for (let cookie of cookies) {
            const [name, value] = cookie.trim().split('=');
            if (name === 'token') {
                token = value;
                break;
            }
        }
    }

    return token;
}

function requireAuth() {
    const token = getToken();
    if (!token) {
        window.location.href = '/login.html';
        return false;
    }
    return true;
}