document.addEventListener('DOMContentLoaded', () => {
    // 1. Tab Switching Logic
    const tabs = {
        'privacy': { btn: document.getElementById('btn-privacy'), panel: document.getElementById('panel-privacy') },
        'terms': { btn: document.getElementById('btn-terms'), panel: document.getElementById('panel-terms') },
        'delete': { btn: document.getElementById('btn-delete'), panel: document.getElementById('panel-delete') }
    };

    window.switchTab = function(tabKey) {
        if (!tabs[tabKey]) return;

        // Reset all tabs
        Object.keys(tabs).forEach(key => {
            tabs[key].btn.classList.remove('active');
            tabs[key].panel.classList.remove('active');
        });

        // Set active tab
        tabs[tabKey].btn.classList.add('active');
        tabs[tabKey].panel.classList.add('active');

        // Scroll to docs container
        const docsSection = document.getElementById('docs');
        if (docsSection) {
            docsSection.scrollIntoView({ behavior: 'smooth' });
        }
    };

    // Attach click handlers to tab buttons
    Object.keys(tabs).forEach(key => {
        tabs[key].btn.addEventListener('click', () => switchTab(key));
    });

    // Navigation links routing to specific tabs
    document.getElementById('nav-privacy').addEventListener('click', (e) => {
        e.preventDefault();
        switchTab('privacy');
    });

    document.getElementById('nav-terms').addEventListener('click', (e) => {
        e.preventDefault();
        switchTab('terms');
    });

    document.getElementById('nav-delete').addEventListener('click', (e) => {
        e.preventDefault();
        switchTab('delete');
    });

    // 2. Form Validation & Submission
    const deleteForm = document.getElementById('delete-request-form');
    const successModal = document.getElementById('success-modal');
    const closeModalBtn = document.getElementById('btn-modal-close');

    deleteForm.addEventListener('submit', (e) => {
        e.preventDefault();

        const nameInput = document.getElementById('del-name');
        const emailInput = document.getElementById('del-email');
        const errorName = document.getElementById('error-name');
        const errorEmail = document.getElementById('error-email');

        let isValid = true;

        // Reset errors
        errorName.style.display = 'none';
        nameInput.style.borderColor = '#cbd5e1';
        errorEmail.style.display = 'none';
        emailInput.style.borderColor = '#cbd5e1';

        // Validate Name
        if (nameInput.value.trim() === '') {
            errorName.style.display = 'block';
            nameInput.style.borderColor = 'var(--error-color)';
            isValid = false;
        }

        // Validate Email
        const emailPattern = /^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/;
        if (emailInput.value.trim() === '') {
            errorEmail.textContent = 'Please enter your email address.';
            errorEmail.style.display = 'block';
            emailInput.style.borderColor = 'var(--error-color)';
            isValid = false;
        } else if (!emailPattern.test(emailInput.value.trim())) {
            errorEmail.textContent = 'Please enter a valid email address.';
            errorEmail.style.display = 'block';
            emailInput.style.borderColor = 'var(--error-color)';
            isValid = false;
        }

        if (isValid) {
            // Simulated submission to server
            // In a real application, you would send this data via fetch()
            console.log('Account Deletion Request submitted:', {
                name: nameInput.value.trim(),
                email: emailInput.value.trim(),
                reason: document.getElementById('del-reason').value,
                message: document.getElementById('del-message').value
            });

            // Show success dialog
            successModal.style.display = 'flex';
            deleteForm.reset();
        }
    });

    // Close Modal Logic
    closeModalBtn.addEventListener('click', () => {
        successModal.style.display = 'none';
    });

    successModal.addEventListener('click', (e) => {
        if (e.target === successModal) {
            successModal.style.display = 'none';
        }
    });

    // 3. Navigation Highlighting on Scroll
    const sections = document.querySelectorAll('section');
    const navLinks = document.querySelectorAll('nav a:not(.btn-download)');

    window.addEventListener('scroll', () => {
        let currentSectionId = '';
        sections.forEach(section => {
            const sectionTop = section.offsetTop;
            const sectionHeight = section.clientHeight;
            if (window.scrollY >= (sectionTop - 150)) {
                currentSectionId = section.getAttribute('id');
            }
        });

        navLinks.forEach(link => {
            link.classList.remove('active');
            if (link.getAttribute('href') === `#${currentSectionId}`) {
                link.classList.add('active');
            }
        });
    });
});
