import React, { FC } from 'react'
import { Outlet } from 'react-router-dom'
import config from '@configs/config.json'
import { SchemaService } from '@services/SchemaService'
import { useAppDispatch } from '@stores/Store'
import { SchemaSlice } from '@stores/slices/SchemaSlice'
import { ServiceContext } from '@contexts/ServiceContext'

// TODO: Update so config can be updated and overriden
const App: FC = () => {
  // Initialize all the required services
  const schemaService = new SchemaService(config.API_URL)
  // Get all schemas for the first time
  useAppDispatch()(async (dispatch, getState) => {
    const state = getState()
    if (!state.schemas.init) {
      Object.entries(await schemaService.getAllSchemas())
        .flatMap((entry) => entry.values)
        .forEach((schema) => dispatch(SchemaSlice.actions.saveSchema(schema)))
      dispatch(SchemaSlice.actions.markInit)
    }
  })
  return (
    <ServiceContext.Provider
      value={{
        schemaService: schemaService
      }}
    >
      <div>
        <Outlet />
      </div>
    </ServiceContext.Provider>
  )
}

export default App
