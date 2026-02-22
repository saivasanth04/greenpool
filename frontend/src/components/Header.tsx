import React from 'react';
import { useNavigate } from 'react-router-dom';
import './Header.css';

const Header: React.FC = () => {
    const navigate = useNavigate();

    return (
        <header className="gp-header">
            <div className="gp-container gp-header-content">
                <div className="gp-logo" onClick={() => navigate('/home')}>
                    Green Pool 🌿
                </div>
                <nav className="gp-nav">
                    {/* Add nav links if needed, e.g., Profile, Logout */}
                </nav>
            </div>
        </header>
    );
};

export default Header;
