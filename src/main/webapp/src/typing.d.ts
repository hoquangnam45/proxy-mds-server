// Create ad-hoc module for custom extension files so that it can be imported like normal ts modules
declare module '*.svg' {
  import * as React from 'react'

  export const ReactComponent: React.FunctionComponent<React.SVGProps<SVGSVGElement> & { title?: string }>

  const src: string
  export default src
}

declare module '*.module.scss'
declare module '*.module.css'
declare module '*.module.less'
