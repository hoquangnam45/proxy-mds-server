import { CracoConfig } from '@craco/types'
import { TsconfigPathsPlugin } from 'tsconfig-paths-webpack-plugin'

// Override webpack configuration so that path aliases from tsconfig.json is automatically applied to webpack with
// no additional configuration and to support loading .less css file
const WebpackConfigOverrides: CracoConfig = {
  plugins: [
    {
      plugin: {
        overrideWebpackConfig: (configOverride) => {
          configOverride.webpackConfig.resolve?.plugins?.push(new TsconfigPathsPlugin({}))
          configOverride.webpackConfig.module?.rules?.push({
            test: /\.less$/i,
            use: [
              // compiles Less to CSS
              'style-loader',
              'css-loader',
              'less-loader'
            ]
          })
          return configOverride.webpackConfig
        }
      }
    }
  ]
}
export default WebpackConfigOverrides
