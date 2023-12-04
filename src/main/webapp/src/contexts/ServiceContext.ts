import { SchemaService } from '@services/SchemaService'
import React from 'react'

export interface ServiceContextType {
  schemaService?: SchemaService
}

export const ServiceContext = React.createContext<ServiceContextType>({})
