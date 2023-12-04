import { configureStore } from '@reduxjs/toolkit'
import { FormSlice } from './slices/FormSlice'
import { useDispatch } from 'react-redux'
import { SchemaSlice } from './slices/SchemaSlice'

export const Store = configureStore({
  reducer: {
    forms: FormSlice.reducer,
    schemas: SchemaSlice.reducer
  },
  preloadedState: ((): any | null => {
    const savedState = localStorage.getItem('appState')
    if (!savedState) {
      return null
    }
    return JSON.parse(savedState)
  })()
})

// Infer the `RootState` and `AppDispatch` types from the store itself
export type RootState = ReturnType<typeof Store.getState>
export type AppDispatch = typeof Store.dispatch
export type AppGetState = typeof Store.getState
export const useAppDispatch: () => AppDispatch = useDispatch // Export a hook that can be reused to resolve types
export default Store

// Persist state to local storage on state change
Store.subscribe(() => {
  const state = Store.getState()
  localStorage.setItem('appState', JSON.stringify(state))
})
