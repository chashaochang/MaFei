const navbar = document.querySelector('.navbar');
const hamburger = document.querySelector('.hamburger');
const navMenu = document.querySelector('.nav-menu');
const navLinks = document.querySelectorAll('.nav-link');
const revealElements = document.querySelectorAll('[data-reveal]');

function setNavbarState() {
    if (!navbar) {
        return;
    }
    navbar.classList.toggle('scrolled', window.scrollY > 12);
}

function closeMenu() {
    if (!hamburger || !navMenu) {
        return;
    }
    hamburger.classList.remove('active');
    hamburger.setAttribute('aria-expanded', 'false');
    navMenu.classList.remove('active');
}

function isMobileMenuOpen() {
    return Boolean(navMenu && navMenu.classList.contains('active'));
}

if (hamburger && navMenu) {
    hamburger.addEventListener('click', () => {
        const expanded = hamburger.classList.toggle('active');
        hamburger.setAttribute('aria-expanded', expanded ? 'true' : 'false');
        navMenu.classList.toggle('active', expanded);
    });
}

navLinks.forEach((link) => {
    link.addEventListener('click', () => {
        closeMenu();
    });
});

document.addEventListener('click', (event) => {
    if (!isMobileMenuOpen() || !hamburger || !navMenu) {
        return;
    }
    const target = event.target;
    if (!(target instanceof Node)) {
        return;
    }
    if (!navMenu.contains(target) && !hamburger.contains(target)) {
        closeMenu();
    }
});

const revealObserver = new IntersectionObserver((entries) => {
    entries.forEach((entry) => {
        if (entry.isIntersecting) {
            entry.target.classList.add('revealed');
            revealObserver.unobserve(entry.target);
        }
    });
}, {
    threshold: 0.14,
    rootMargin: '0px 0px -40px 0px'
});

revealElements.forEach((element) => {
    revealObserver.observe(element);
});

const backToTopBtn = document.createElement('button');
backToTopBtn.className = 'back-to-top';
backToTopBtn.type = 'button';
backToTopBtn.setAttribute('aria-label', '回到顶部');
backToTopBtn.textContent = '↑';
document.body.appendChild(backToTopBtn);

backToTopBtn.addEventListener('click', () => {
    window.scrollTo({ top: 0, behavior: 'smooth' });
});

window.addEventListener('scroll', () => {
    setNavbarState();
    backToTopBtn.classList.toggle('show', window.scrollY > 500);
});

window.addEventListener('keydown', (event) => {
    if (event.key === 'Escape') {
        closeMenu();
    }
});

window.addEventListener('resize', () => {
    if (window.innerWidth > 768) {
        closeMenu();
    }
});

document.querySelectorAll('img').forEach((img) => {
    img.addEventListener('error', () => {
        img.style.opacity = '0';
    });
});

setNavbarState();
