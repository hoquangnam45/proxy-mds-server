import { createSlice } from '@reduxjs/toolkit'

export interface SchemaState {
  data: Partial<Record<string, Partial<Record<string, any>>>>
  init: boolean
}
export const SchemaSlice = createSlice({
  name: 'schema',
  initialState: (): SchemaState => ({
    data: {},
    init: false
  }),
  reducers: {
    saveSchema: (state, action) => {
      const schemaName: string = action.payload.schemaName
      const subSchemaName: string = action.payload.subSchemaName
      if (!state.data[schemaName]) {
        state.data[schemaName] = {}
      }
      state.data[schemaName]![subSchemaName] = action.payload.form
    },
    clearSchema: (state, action) => {
      const schemaName: string = action.payload.schemaName
      const subSchemaName: string = action.payload.subSchemaName
      delete state.data[schemaName]?.[subSchemaName]
    },
    markInit: (state) => {
      state.init = true
    }
    clearAllSchema: () => ({
      data: {},
      init: false
    })
  }
})
