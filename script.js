document.addEventListener('DOMContentLoaded', () => {
    // 1. Tab Switching Logic
    const tabs = {
        'privacy': { btn: document.getElementById('btn-privacy'), panel: document.getElementById('panel-privacy') },
        'terms': { btn: document.getElementById('btn-terms'), panel: document.getElementById('panel-terms') }
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
