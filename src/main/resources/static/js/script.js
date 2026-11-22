// KeyJolt Frontend JavaScript
class KeyJoltApp {
    constructor() {
        this.form = document.getElementById('keyGenerationForm');
        this.generateBtn = document.getElementById('generateBtn');
        this.resultsSection = document.getElementById('resultsSection');
        this.errorSection = document.getElementById('errorSection');
        this.downloadGrid = document.getElementById('downloadGrid');
        this.successMessage = document.getElementById('successMessage');
        this.errorMessage = document.getElementById('errorMessage');

        // Mode (PGP + SSH vs SSH only)
        this.pgpFields = document.getElementById('pgpFields');
        this.sshFields = document.getElementById('sshFields');
        this.btnText = this.generateBtn.querySelector('.btn-text');
        this.sshSummary = document.getElementById('sshSummary');
        this.sshFingerprint = document.getElementById('sshFingerprint');
        this.sshPublicKey = document.getElementById('sshPublicKey');
        this.copyPublicKeyBtn = document.getElementById('copyPublicKey');

        // Modal elements
        this.aboutModal = document.getElementById('aboutModal');
        this.securityModal = document.getElementById('security-modal');
        this.privacyModal = document.getElementById('privacy-modal');

        this.aboutLink = document.querySelector('a[href="#about"]');
        this.securityLink = document.querySelector('a[href="#security"]');
        this.privacyLink = document.querySelector('a[href="#privacy"]');

        this.closeAboutModalBtn = this.aboutModal ? this.aboutModal.querySelector('.modal-close') : null;
        this.securityModalCloseBtn = this.securityModal ? this.securityModal.querySelector('.close-button') : null;
        this.privacyModalCloseBtn = this.privacyModal ? this.privacyModal.querySelector('.close-button') : null;

        // Screen-reader announcements for long-running generation
        this.generationStatus = document.getElementById('generationStatus');

        this.validationTimeouts = {};
        this.isGenerating = false;
        
        this.init();
    }
    
    init() {
        // Initialize Feather icons
        if (typeof feather !== 'undefined') {
            feather.replace();
        }
        
        // Setup event listeners
        this.setupEventListeners();
        
        // Setup tooltips
        this.setupTooltips();
        
        // Setup form validation
        this.setupValidation();

        // Setup Modals
        this.initModals();

        // Setup PGP / SSH-only mode toggle
        this.setupModeToggle();
    }

    getMode() {
        const checked = this.form.querySelector('input[name="mode"]:checked');
        return checked ? checked.value : 'pgp';
    }

    setupModeToggle() {
        this.form.querySelectorAll('input[name="mode"]').forEach(radio => {
            radio.addEventListener('change', () => {
                this.applyMode();
                this.hideResults();
                // Keep a shareable link: keyjolt.dev/#ssh opens SSH-only mode
                try {
                    history.replaceState(null, '', this.getMode() === 'ssh' ? '#ssh' : window.location.pathname);
                } catch (_) { /* ignore */ }
            });
        });

        if (window.location.hash === '#ssh') {
            document.getElementById('modeSsh').checked = true;
        }
        this.applyMode();

        const comment = document.getElementById('sshComment');
        if (comment) {
            comment.addEventListener('input', () => this.validateSshComment());
            comment.addEventListener('blur', () => this.validateSshComment());
        }
        const passphrase = document.getElementById('sshPassphrase');
        if (passphrase) {
            passphrase.addEventListener('blur', () => this.validateSshPassphrase());
        }

        if (this.copyPublicKeyBtn) {
            this.copyPublicKeyBtn.addEventListener('click', () => this.copyPublicKey());
        }
    }

    applyMode() {
        const ssh = this.getMode() === 'ssh';
        // A disabled fieldset is excluded from validation and submission
        this.pgpFields.hidden = ssh;
        this.pgpFields.disabled = ssh;
        this.sshFields.hidden = !ssh;
        this.sshFields.disabled = !ssh;
        if (this.btnText) {
            this.btnText.textContent = ssh ? 'Generate SSH Key' : 'Generate Keys';
        }
    }

    validateSshComment() {
        const value = document.getElementById('sshComment').value;
        if (value.length > 100) {
            this.updateFieldValidation('sshComment', false, 'Comment must not exceed 100 characters');
            return false;
        }
        if (!/^[A-Za-z0-9@._+\- ]*$/.test(value)) {
            this.updateFieldValidation('sshComment', false, 'Comment can only contain letters, numbers, spaces and @ . _ + -');
            return false;
        }
        this.updateFieldValidation('sshComment', true, '');
        return true;
    }

    validateSshPassphrase() {
        const value = document.getElementById('sshPassphrase').value;
        if (value.length > 0 && value.length < 8) {
            this.updateFieldValidation('sshPassphrase', false, 'Passphrase must be at least 8 characters');
            return false;
        }
        this.updateFieldValidation('sshPassphrase', true, '');
        return true;
    }

    async copyPublicKey() {
        const text = this.sshPublicKey.textContent;
        const label = this.copyPublicKeyBtn.querySelector('.btn-copy-text');
        let ok = false;
        try {
            await navigator.clipboard.writeText(text);
            ok = true;
        } catch (_) {
            // Fallback: select the key so the user can copy it manually
            const range = document.createRange();
            range.selectNodeContents(this.sshPublicKey);
            const sel = window.getSelection();
            sel.removeAllRanges();
            sel.addRange(range);
        }
        if (label) {
            label.textContent = ok ? 'Copied' : 'Press Ctrl+C';
            setTimeout(() => { label.textContent = 'Copy'; }, 2000);
        }
    }

    // Generic modal handling: role="dialog" + aria-modal in the markup;
    // here focus moves in, is trapped, and returns to the trigger on close.
    openModal(modalElement, trigger) {
        if (!modalElement) return;

        document.querySelectorAll('.modal.modal-open').forEach(m => {
            if (m !== modalElement) this.closeModal(m, false);
        });

        modalElement.returnFocusTo = trigger || document.activeElement;
        modalElement.hidden = false;
        modalElement.classList.add('modal-open');
        document.body.classList.add('modal-locked');

        const focusable = this.focusableIn(modalElement);
        if (focusable.length) {
            focusable[0].focus();
        } else {
            modalElement.setAttribute('tabindex', '-1');
            modalElement.focus();
        }
    }

    closeModal(modalElement, restoreFocus = true) {
        if (!modalElement || modalElement.hidden) return;

        modalElement.classList.remove('modal-open');
        modalElement.hidden = true;
        if (!document.querySelector('.modal.modal-open')) {
            document.body.classList.remove('modal-locked');
        }

        const target = modalElement.returnFocusTo;
        modalElement.returnFocusTo = null;
        if (restoreFocus && target && document.body.contains(target) && typeof target.focus === 'function') {
            target.focus();
        }
    }

    focusableIn(container) {
        return Array.from(container.querySelectorAll(
            'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex]:not([tabindex="-1"])'
        )).filter(el => !el.hidden && el.offsetParent !== null);
    }

    // Keep Tab / Shift+Tab inside the open dialog
    trapFocus(event, modalElement) {
        const focusable = this.focusableIn(modalElement);
        if (!focusable.length) {
            event.preventDefault();
            return;
        }
        const first = focusable[0];
        const last = focusable[focusable.length - 1];
        if (event.shiftKey && document.activeElement === first) {
            event.preventDefault();
            last.focus();
        } else if (!event.shiftKey && document.activeElement === last) {
            event.preventDefault();
            first.focus();
        }
    }

    setupEventListeners() {
        // Form submission
        this.form.addEventListener('submit', (e) => this.handleFormSubmit(e));
        
        // Real-time validation
        const inputs = ['name', 'email', 'keyExpiry'];
        inputs.forEach(field => {
            const input = document.getElementById(field);
            if (input) {
                input.addEventListener('input', () => this.validateField(field, input.value));
                input.addEventListener('blur', () => this.validateField(field, input.value));
            }
        });

        const passwordInput = document.getElementById('password');
        if (passwordInput) {
            passwordInput.addEventListener('blur', () => this.validatePasswordField(passwordInput));
        }
        
        // "Generate New Keys" / "Try Again": bound here because the CSP
        // (script-src 'self') blocks inline onclick handlers.
        document.querySelectorAll('.js-reset').forEach(btn => {
            btn.addEventListener('click', () => window.resetForm());
        });

        // Prevent form submission while generating
        this.form.addEventListener('keydown', (e) => {
            if (e.key === 'Enter' && this.isGenerating) {
                e.preventDefault();
            }
        });
    }
    
    setupTooltips() {
        const tooltipTriggers = document.querySelectorAll('.tooltip-trigger');
        const tooltip = document.getElementById('tooltip');
        
        if (!tooltip) return;

        const show = (trigger) => {
            const tooltipText = trigger.getAttribute('data-tooltip');
            if (tooltipText) {
                tooltip.textContent = tooltipText;
                this.positionTooltip(trigger, tooltip);
                tooltip.classList.add('show');
            }
        };
        const hide = () => tooltip.classList.remove('show');

        tooltipTriggers.forEach(trigger => {
            // Keyboard and screen-reader users get the same help text
            if (!trigger.hasAttribute('aria-label')) {
                trigger.setAttribute('aria-label', trigger.getAttribute('data-tooltip') || 'Help');
            }
            trigger.setAttribute('role', 'note');
            trigger.addEventListener('mouseenter', () => show(trigger));
            trigger.addEventListener('mouseleave', hide);
            trigger.addEventListener('focus', () => show(trigger));
            trigger.addEventListener('blur', hide);
        });
    }
    
    positionTooltip(trigger, tooltip) {
        const triggerRect = trigger.getBoundingClientRect();
        const tooltipRect = tooltip.getBoundingClientRect();
        
        let left = triggerRect.left + (triggerRect.width / 2) - (tooltipRect.width / 2);
        let top = triggerRect.top - tooltipRect.height - 8;
        
        // Adjust if tooltip goes off screen
        if (left < 8) left = 8;
        if (left + tooltipRect.width > window.innerWidth - 8) {
            left = window.innerWidth - tooltipRect.width - 8;
        }
        if (top < 8) {
            top = triggerRect.bottom + 8;
        }
        
        tooltip.style.left = left + 'px';
        tooltip.style.top = top + 'px';
    }
    
    setupValidation() {
        // Set default encryption strength
        const encryptionSelect = document.getElementById('encryptionStrength');
        if (encryptionSelect && !encryptionSelect.value) {
            encryptionSelect.value = '4096';
        }
    }
    
    async validateField(field, value) {
        // Clear previous timeout
        if (this.validationTimeouts[field]) {
            clearTimeout(this.validationTimeouts[field]);
        }
        
        // Debounce validation
        this.validationTimeouts[field] = setTimeout(async () => {
            try {
                const response = await this.fetchWithTimeout('/api/validate', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                    },
                    body: `field=${encodeURIComponent(field)}&value=${encodeURIComponent(value)}`
                });
                
                if (!response.ok) return; // e.g. rate limited: keep the client-side checks only
                const result = await response.json();
                this.updateFieldValidation(field, result.valid, result.message);
            } catch (_) {
                // Live validation is a convenience; the server validates again on submit.
            }
        }, 300);
    }
    
    updateFieldValidation(field, isValid, message) {
        const input = document.getElementById(field);
        const errorDiv = document.getElementById(field + 'Error');
        
        if (!input || !errorDiv) return;
        
        // Remove existing classes
        input.classList.remove('error', 'success');
        errorDiv.classList.remove('show');
        
        if (message && !isValid) {
            // Show error
            input.classList.add('error');
            errorDiv.textContent = message;
            errorDiv.classList.add('show');
        } else if (input.value.trim() !== '') {
            // Show success for non-empty valid fields
            input.classList.add('success');
        }
    }
    
    validatePasswordField(input) {
        const value = input.value;
        if (value && value.length > 0 && value.length < 8) {
            this.updateFieldValidation('password', false, 'Password must be at least 8 characters');
        } else {
            this.updateFieldValidation('password', true, '');
        }
    }

    async handleFormSubmit(e) {
        e.preventDefault();
        
        if (this.isGenerating) return;
        
        // Hide previous results/errors
        this.hideResults();
        
        // Validate form
        if (!this.validateForm()) {
            return;
        }
        
        // Start generation
        this.startGeneration();
        
        try {
            const ssh = this.getMode() === 'ssh';
            const formData = ssh ? this.collectSshFormData() : this.collectFormData();
            const response = await this.generateKeys(ssh ? '/api/generate/ssh' : '/api/generate', formData);
            
            if (response.success) {
                this.showSuccess(response);
            } else {
                this.showError(response.error || 'Key generation failed');
            }
            
        } catch (error) {
            if (error.name === 'AbortError') {
                this.showError('The server took too long to answer. Please try again.');
            } else if (error instanceof TypeError && error.message === 'Failed to fetch') {
                this.showError('Network error. Please check your connection and try again.');
            } else {
                this.showError('An unexpected error occurred. Please try again.');
            }
        } finally {
            this.stopGeneration();
        }
    }
    
    validateForm() {
        if (this.getMode() === 'ssh') {
            const commentOk = this.validateSshComment();
            const passphraseOk = this.validateSshPassphrase();
            return commentOk && passphraseOk;
        }

        const requiredFields = ['name', 'email', 'encryptionStrength', 'keyExpiry'];
        let isValid = true;
        
        requiredFields.forEach(field => {
            const input = document.getElementById(field);
            if (!input || !input.value.trim()) {
                this.updateFieldValidation(field, false, 'This field is required');
                isValid = false;
            }
        });
        
        // Additional validation
        const email = document.getElementById('email').value;
        if (email && !this.isValidEmail(email)) {
            this.updateFieldValidation('email', false, 'Please enter a valid email address');
            isValid = false;
        }
        
        const keyExpiry = parseInt(document.getElementById('keyExpiry').value);
        if (isNaN(keyExpiry) || keyExpiry < 0 || keyExpiry > 3650) {
            this.updateFieldValidation('keyExpiry', false, 'Key expiry must be between 0 and 3650 days');
            isValid = false;
        }

        const password = document.getElementById('password') ? document.getElementById('password').value : '';
        if (password.length > 0 && password.length < 8) {
            this.updateFieldValidation('password', false, 'Password must be at least 8 characters');
            isValid = false;
        }
        
        return isValid;
    }
    
    isValidEmail(email) {
        const emailRegex = /^[A-Za-z0-9+_.-]+@([A-Za-z0-9.-]+\.[A-Za-z]{2,})$/;
        return emailRegex.test(email);
    }
    
    collectFormData() {
        return {
            name: document.getElementById('name').value.trim(),
            email: document.getElementById('email').value.trim(),
            encryptionStrength: parseInt(document.getElementById('encryptionStrength').value),
            keyExpiry: parseInt(document.getElementById('keyExpiry').value),
            generateSshKey: document.getElementById('generateSshKey').checked,
            // Add password to form data, it's okay if it's null or empty
            password: document.getElementById('password') ? document.getElementById('password').value : null
        };
    }
    
    collectSshFormData() {
        return {
            keyType: document.getElementById('sshKeyType').value,
            comment: document.getElementById('sshComment').value.trim(),
            passphrase: document.getElementById('sshPassphrase').value || null
        };
    }

    // fetch() with an upper bound, so a stalled request ends with a message
    // instead of a spinner that never stops (KJ-09).
    async fetchWithTimeout(url, options = {}, timeoutMs = 10000) {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), timeoutMs);
        try {
            return await fetch(url, { ...options, signal: controller.signal });
        } finally {
            clearTimeout(timer);
        }
    }

    async generateKeys(url, formData) {
        // RSA 4096 can take tens of seconds on the Pi; nginx gives up at 75 s.
        const response = await this.fetchWithTimeout(url, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(formData)
        }, 80000);

        if (response.status === 429) {
            return { success: false, error: 'Rate limit exceeded: at most 10 key generations per hour and 5 per minute. Please wait before generating more keys.' };
        }

        if (response.status === 503) {
            const retry = parseInt(response.headers.get('Retry-After'), 10);
            const wait = Number.isFinite(retry) && retry > 0 ? `${retry} seconds` : 'a few seconds';
            return { success: false, error: `The server is busy generating other keys. Please try again in ${wait}.` };
        }

        if (!response.ok) {
            try {
                return await response.json();
            } catch (_) {
                return { success: false, error: 'Server error. Please try again.' };
            }
        }

        return await response.json();
    }
    
    startGeneration() {
        this.isGenerating = true;
        this.generateBtn.disabled = true;
        this.generateBtn.classList.add('loading');
        this.generateBtn.setAttribute('aria-busy', 'true');
        this.announce(this.getMode() === 'ssh'
            ? 'Generating your SSH key…'
            : 'Generating your keys. RSA keys can take up to a minute…');
        
        // Disable form inputs
        const inputs = this.form.querySelectorAll('input, select, button');
        inputs.forEach(input => input.disabled = true);
    }
    
    stopGeneration() {
        this.isGenerating = false;
        this.generateBtn.disabled = false;
        this.generateBtn.classList.remove('loading');
        this.generateBtn.removeAttribute('aria-busy');
        
        // Re-enable form inputs
        const inputs = this.form.querySelectorAll('input, select, button');
        inputs.forEach(input => input.disabled = false);
    }
    
    announce(text) {
        if (this.generationStatus) {
            this.generationStatus.textContent = text;
        }
    }

    showSuccess(response) {
        this.successMessage.textContent = response.message;
        if (response.publicKey) {
            this.sshPublicKey.textContent = response.publicKey;
            this.sshFingerprint.textContent = response.fingerprint || '';
            this.sshSummary.hidden = false;
        } else {
            this.sshSummary.hidden = true;
        }
        this.renderDownloadLinks(response.files);
        this.announce('');
        this.resultsSection.hidden = false;
        this.resultsSection.classList.add('fade-in');
        this.resultsSection.scrollIntoView({ behavior: 'smooth' });
    }
    
    showError(errorText) {
        this.errorMessage.textContent = errorText;
        this.announce('');
        this.errorSection.hidden = false;
        this.errorSection.classList.add('fade-in');
        this.errorSection.scrollIntoView({ behavior: 'smooth' });
    }
    
    hideResults() {
        this.resultsSection.hidden = true;
        this.errorSection.hidden = true;
        this.resultsSection.classList.remove('fade-in');
        this.errorSection.classList.remove('fade-in');
    }
    
    renderDownloadLinks(files) {
        this.downloadGrid.replaceChildren();
        
        const fileTypeInfo = {
            'pgp_public': {
                title: 'PGP Public Key',
                icon: 'unlock',
                description: 'Share this with others to encrypt messages for you'
            },
            'pgp_private': {
                title: 'PGP Private Key',
                icon: 'lock',
                description: 'Keep this secret! Used to decrypt messages'
            },
            'ssh_public': {
                title: 'SSH Public Key',
                icon: 'terminal',
                description: 'Add this to servers for authentication'
            },
            'ssh_private': {
                title: 'SSH Private Key',
                icon: 'key',
                description: 'Keep this secret! Used for server access'
            }
        };
        
        files.forEach(file => {
            const typeInfo = fileTypeInfo[file.type] || {
                title: 'Key File',
                icon: 'file',
                description: 'Generated key file'
            };

            const el = (tag, className, text) => {
                const node = document.createElement(tag);
                if (className) node.className = className;
                if (text !== undefined) node.textContent = text;
                return node;
            };
            const icon = (name, className) => {
                const i = el('i', className);
                i.setAttribute('data-feather', name);
                i.setAttribute('aria-hidden', 'true');
                return i;
            };

            const downloadItem = el('div', 'download-item');

            const header = el('div', 'download-header');
            header.append(icon(typeInfo.icon, 'download-icon'), el('h3', 'download-title', typeInfo.title));

            const info = el('div', 'download-info');
            info.append(
                document.createTextNode(typeInfo.description), el('br'),
                el('strong', null, 'File:'), document.createTextNode(' ' + file.filename), el('br'),
                el('strong', null, 'Size:'), document.createTextNode(' ' + this.formatFileSize(file.size))
            );

            const link = el('a', 'download-link');
            link.href = file.downloadUrl;
            link.dataset.filename = file.filename;
            link.setAttribute('aria-label', `Download ${typeInfo.title} (${file.filename})`);
            link.append(icon('download', 'download-link-icon'), document.createTextNode(' Download'));
            link.addEventListener('click', (e) => {
                e.preventDefault();
                this.downloadFile(file.downloadUrl, file.filename);
            });

            downloadItem.append(header, info, link);
            this.downloadGrid.appendChild(downloadItem);
        });

        // Re-initialize Feather icons for new elements
        if (typeof feather !== 'undefined') {
            feather.replace();
        }
    }
    
    formatFileSize(bytes) {
        if (bytes === 0) return '0 B';
        const k = 1024;
        const sizes = ['B', 'KB', 'MB'];
        const i = Math.floor(Math.log(bytes) / Math.log(k));
        return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i];
    }

    async downloadFile(downloadUrl, filename) {
        try {
            const response = await this.fetchWithTimeout(downloadUrl, {}, 30000);

            if (!response.ok) {
                // Key files are deleted 5 minutes after generation, so a 404
                // here almost always means the link has expired (KJ-09).
                if (response.status === 404 || response.status === 410) {
                    this.showError('This download link has expired: keys are deleted 5 minutes after generation. Please generate a new key.');
                } else if (response.status === 400) {
                    this.showError('This download link is not valid. Please generate a new key.');
                } else {
                    this.showError(`Download failed (HTTP ${response.status}). Please try again.`);
                }
                return;
            }

            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = filename;
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
        } catch (error) {
            this.showError(error.name === 'AbortError'
                ? 'The download took too long. Please try again.'
                : 'Download failed. Please check your connection and try again.');
        }
    }

    initModals() {
        const modalsMap = [
            { link: this.aboutLink, modal: this.aboutModal, closeBtn: this.closeAboutModalBtn },
            { link: this.securityLink, modal: this.securityModal, closeBtn: this.securityModalCloseBtn },
            { link: this.privacyLink, modal: this.privacyModal, closeBtn: this.privacyModalCloseBtn }
        ];

        modalsMap.forEach(item => {
            if (item.link && item.modal) {
                item.link.setAttribute('aria-haspopup', 'dialog');
                item.link.addEventListener('click', (e) => {
                    e.preventDefault();
                    this.openModal(item.modal, item.link);
                });
            }
            if (item.closeBtn && item.modal) {
                item.closeBtn.addEventListener('click', () => this.closeModal(item.modal));
            }
            if (item.modal) {
                // Backdrop click (outside the dialog content) closes it
                item.modal.addEventListener('click', (event) => {
                    if (event.target === item.modal) this.closeModal(item.modal);
                });
            }
        });

        document.addEventListener('keydown', (event) => {
            const openModal = document.querySelector('.modal.modal-open');
            if (!openModal) return;
            if (event.key === 'Escape') {
                event.preventDefault();
                this.closeModal(openModal);
            } else if (event.key === 'Tab') {
                this.trapFocus(event, openModal);
            }
        });
    }
}

// Global functions
window.resetForm = function() {
    const app = window.keyJoltApp;
    if (app) {
        const mode = app.getMode();
        app.hideResults();
        app.form.reset();
        // Stay in the mode the user picked
        document.getElementById(mode === 'ssh' ? 'modeSsh' : 'modePgp').checked = true;
        app.applyMode();
        
        // Reset validation states
        const inputs = app.form.querySelectorAll('input, select');
        inputs.forEach(input => {
            input.classList.remove('error', 'success');
        });
        
        const errors = app.form.querySelectorAll('.field-error');
        errors.forEach(error => {
            error.classList.remove('show');
        });
        
        // Set default encryption strength
        const encryptionSelect = document.getElementById('encryptionStrength');
        if (encryptionSelect) {
            encryptionSelect.value = '4096';
        }
        
        // Scroll to top
        window.scrollTo({ top: 0, behavior: 'smooth' });
    }
};

// Initialize app when DOM is loaded
document.addEventListener('DOMContentLoaded', function() {
    window.keyJoltApp = new KeyJoltApp();
});

// Handle beforeunload to warn about ongoing operations
window.addEventListener('beforeunload', function(e) {
    const app = window.keyJoltApp;
    if (app && app.isGenerating) {
        e.preventDefault();
        e.returnValue = 'Key generation is in progress. Are you sure you want to leave?';
        return e.returnValue;
    }
});
