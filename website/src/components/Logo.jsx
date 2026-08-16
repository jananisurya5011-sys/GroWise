import React from 'react';

const Logo = ({ size = 100, className = '' }) => (
  <svg
    xmlns="http://www.w3.org/2000/svg"
    viewBox="0 0 100 100"
    width={size}
    height={size}
    className={className}
  >
    <path
      fill="var(--peach-background, #FCDABB)"
      d="M20,10 H80 A10,10 0,0 1,90 20 V80 A10,10 0,0 1,80 90 H20 A10,10 0,0 1,10 80 V20 A10,10 0,0 1,20 10 Z"
    />
    <path
      fill="var(--terracotta-primary, #A03C23)"
      d="M35,70 H65 A3,3 0,0 1,65 76 H35 A3,3 0,0 1,35 70 Z"
    />
    <path
      fill="var(--terracotta-primary, #A03C23)"
      d="M50,65 C35,65 30,50 30,40 C30,30 45,35 50,45 Z"
    />
    <path
      fill="var(--golden-yellow, #F2A33A)"
      d="M50,65 C65,65 70,45 70,30 C70,20 55,25 50,40 Z"
    />
    <path
      fill="var(--terracotta-primary, #A03C23)"
      d="M65,22 A4,4 0,1 1,73 22 A4,4 0,1 1,65 22 Z"
    />
  </svg>
);

export default Logo;
