import React from 'react';
import { Loader2 } from 'lucide-react';

interface ButtonLoaderProps {
  size?: number;
  className?: string;
}

export const ButtonLoader: React.FC<ButtonLoaderProps> = ({
  size = 16,
  className = '',
}) => {
  return (
    <Loader2 
      className={`animate-spin ${className}`} 
      style={{ width: size, height: size }}
    />
  );
};

export default ButtonLoader;
