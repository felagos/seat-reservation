import { create } from 'zustand';

interface UIState {
  error?: string;
  setError: (message: string) => void;
  clearError: () => void;
}

export const useUIStore = create<UIState>((set) => ({
  error: undefined,
  setError: (message) => set({ error: message }),
  clearError: () => set({ error: undefined }),
}));
