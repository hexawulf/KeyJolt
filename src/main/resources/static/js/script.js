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

        // Modal elements
        this.aboutModal = document.getElementById('aboutModal');
        this.securityModal = document.getElementById('security-modal');
        this.privacyModal = document.getElementById('privacy-modal');

        this.aboutLink = document.querySelector('a[href="#about"]');
        this.securityLink = document.querySelector('a[href="#security"]');
        this.privacyLink = document.querySelector('a[href="#privacy"]');

        this.closeAboutModalBtn = this.aboutModal ? this.aboutModal.querySelector('.modal-close') : null; // Existing about modal close
        this.securityModalCloseBtn = this.securityModal ? this.securityModal.querySelector('.close-button') : null;
        this.privacyModalCloseBtn = this.privacyModal ? this.privacyModal.querySelector('.close-button') : null;
        
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
    }

    // Generic Modal Handling
    openModal(modalElement) {
        if (!modalElement) return;

        // Close any other open modals first
        document.querySelectorAll('.modal.modal-open').forEach(m => {
            if (m !== modalElement) {
                // Assuming closeModal handles the actual hiding and class removal
                this.closeModal(m);
            }
        });

        // Show the modal
        modalElement.classList.add('modal-open');
        // modalElement.style.display = 'flex'; // Ensure it shows - CSS .modal.modal-open handles this with !important

        // Focus management
        // Attempt to focus the first focusable element in the modal
        const focusable = modalElement.querySelector('button, [href], input, select, textarea, [tabindex]:not([tabindex="-1"])');
        if (focusable) {
            focusable.focus();
        } else { // Fallback if no focusable element is found
            modalElement.setAttribute('tabindex', '-1'); // Ensure modal itself is focusable
            modalElement.focus();
        }

        // Store the trigger link if available (assuming it's set on modalElement by initModals)
        // This part depends on how `triggerLink` is associated with modalElement.
        // If `this.activeModalTrigger` is the way to store it:
        // this.activeModalTrigger = modalElement.triggerLink; // or however the trigger is passed/stored
    }

    closeModal(modalElement) {
        if (!modalElement) return;

        const triggerLink = modalElement.triggerLink; // Assuming triggerLink is a property of modalElement

        modalElement.classList.remove('modal-open');
        modalElement.style.display = 'none'; // Ensure it hides

        // Restore focus to the trigger link if it exists and is part of the document
        if (triggerLink && document.body.contains(triggerLink)) {
            triggerLink.focus();
        } else {
            // Fallback focus if triggerLink is not available or not in DOM
            // This might be the case if modal was closed by ESC or backdrop click
            // Or if triggerLink was not correctly associated.
            // Focusing body or a relevant container can be a fallback.
            // For now, we rely on the triggerLink being correctly set.
        }

        // If this.activeModal was used to track the currently open modal
        // if (this.activeModal === modalElement) {
        //     this.activeModal = null;
        //     this.activeModalTrigger = null;
        // }
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
        
        tooltipTriggers.forEach(trigger => {
            trigger.addEventListener('mouseenter', (e) => {
                const tooltipText = trigger.getAttribute('data-tooltip');
                if (tooltipText) {
                    tooltip.textContent = tooltipText;
                    this.positionTooltip(e.target, tooltip);
                    tooltip.classList.add('show');
                }
            });
            
            trigger.addEventListener('mouseleave', () => {
                tooltip.classList.remove('show');
            });
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
                const response = await fetch('/api/validate', {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/x-www-form-urlencoded',
                    },
                    body: `field=${encodeURIComponent(field)}&value=${encodeURIComponent(value)}`
                });
                
                const result = await response.json();
                this.updateFieldValidation(field, result.valid, result.message);
                
            } catch (error) {
                console.error('Validation error:', error);
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
            const formData = this.collectFormData();
            const response = await this.generateKeys(formData);
            
            if (response.success) {
                this.showSuccess(response);
            } else {
                this.showError(response.error || 'Key generation failed');
            }
            
        } catch (error) {
            console.error('Generation error:', error);
            this.showError('Network error. Please check your connection and try again.');
        } finally {
            this.stopGeneration();
        }
    }
    
    validateForm() {
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
    
    async generateKeys(formData) {
        const response = await fetch('/api/generate', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                'Accept': 'application/json'
            },
            body: JSON.stringify(formData)
        });
        
        if (!response.ok) {
            if (response.status === 429) {
                throw new Error('Rate limit exceeded. Please wait before generating more keys.');
            }
            throw new Error(`HTTP error! status: ${response.status}`);
        }
        
        return await response.json();
    }
    
    startGeneration() {
        this.isGenerating = true;
        this.generateBtn.disabled = true;
        this.generateBtn.classList.add('loading');
        
        // Disable form inputs
        const inputs = this.form.querySelectorAll('input, select, button');
        inputs.forEach(input => input.disabled = true);
    }
    
    stopGeneration() {
        this.isGenerating = false;
        this.generateBtn.disabled = false;
        this.generateBtn.classList.remove('loading');
        
        // Re-enable form inputs
        const inputs = this.form.querySelectorAll('input, select, button');
        inputs.forEach(input => input.disabled = false);
    }
    
    showSuccess(response) {
        this.successMessage.textContent = response.message;
        this.renderDownloadLinks(response.files);
        this.resultsSection.style.display = 'block';
        this.resultsSection.classList.add('fade-in');
        this.resultsSection.scrollIntoView({ behavior: 'smooth' });
    }
    
    showError(errorText) {
        this.errorMessage.textContent = errorText;
        this.errorSection.style.display = 'block';
        this.errorSection.classList.add('fade-in');
        this.errorSection.scrollIntoView({ behavior: 'smooth' });
    }
    
    hideResults() {
        this.resultsSection.style.display = 'none';
        this.errorSection.style.display = 'none';
        this.resultsSection.classList.remove('fade-in');
        this.errorSection.classList.remove('fade-in');
    }
    
    renderDownloadLinks(files) {
        this.downloadGrid.innerHTML = '';
        
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
            
            const downloadItem = document.createElement('div');
            downloadItem.className = 'download-item';
            downloadItem.innerHTML = `
                <div class="download-header">
                    <i data-feather="${typeInfo.icon}" class="download-icon"></i>
                    <h3 class="download-title">${typeInfo.title}</h3>
                </div>
                <div class="download-info">
                    ${typeInfo.description}<br>
                    <strong>File:</strong> ${file.filename}<br>
                    <strong>Size:</strong> ${this.formatFileSize(file.size)}
                </div>
                <a href="${file.downloadUrl}" class="download-link" download="${file.filename}">
                    <i data-feather="download" class="download-link-icon"></i>
                    Download
                </a>
            `;
            
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

    initModals() {
        const modalsMap = [
            { link: this.aboutLink, modal: this.aboutModal, closeBtn: this.closeAboutModalBtn },
            { link: this.securityLink, modal: this.securityModal, closeBtn: this.securityModalCloseBtn },
            { link: this.privacyLink, modal: this.privacyModal, closeBtn: this.privacyModalCloseBtn }
        ];

        modalsMap.forEach(item => {
            if (item.link && item.modal) {
                item.modal.triggerLink = item.link; // Store trigger link
                item.link.addEventListener('click', (e) => {
                    e.preventDefault();
                    this.openModal(item.modal);
                });
            }
            if (item.closeBtn && item.modal) {
                item.closeBtn.addEventListener('click', () => {
                    this.closeModal(item.modal);
                });
            }
        });

        // Window click to close any open modal
        window.addEventListener('click', (event) => {
            document.querySelectorAll('.modal.modal-open').forEach(modal => {
                if (event.target === modal) { // Clicked on the modal backdrop
                    this.closeModal(modal);
                }
            });
        });

        // Escape key to close any open modal
        window.addEventListener('keydown', (event) => {
            if (event.key === 'Escape') {
                const openModal = document.querySelector('.modal.modal-open');
                if (openModal) {
                    this.closeModal(openModal);
                }
            }
        });
    }
}

// Global functions
window.resetForm = function() {
    const app = window.keyJoltApp;
    if (app) {
        app.hideResults();
        app.form.reset();
        
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

// Handle page visibility changes to clean up resources
document.addEventListener('visibilitychange', function() {
    if (document.hidden) {
        // Page is hidden, clean up any ongoing operations
        const app = window.keyJoltApp;
        if (app && app.isGenerating) {
            console.log('Page hidden during generation');
        }
    }
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
