import { HvigorPlugin } from '@ohos/hvigor';
import { appTasks, OhosAppContext, OhosPluginId } from '@ohos/hvigor-ohos-plugin';

const { resolveBundleName } = require('../runscript/resolveOhosBundle.cjs');

const developmentBundle: HvigorPlugin = {
  pluginId: 'mafei.development-bundle',
  apply(node) {
    node.afterNodeEvaluate(() => {
      const context = node.getContext(OhosPluginId.OHOS_APP_PLUGIN) as OhosAppContext;
      const appJson = context.getAppJsonOpt();
      appJson.app.bundleName = resolveBundleName(appJson.app.bundleName, context.getBuildMode());
      context.setAppJsonOpt(appJson);
      // Local signing material stays outside version control. Release keeps the
      // product's original signing config; Debug may select its dedicated one.
      if (context.getBuildMode() === 'debug') {
        const profile = context.getBuildProfileOpt();
        const development = profile.app.signingConfigs?.find(item => item.name === 'development');
        if (development) {
          const productName = context.getCurrentProduct().getProductName();
          const product = profile.app.products?.find(item => item.name === productName);
          if (!product) throw new Error(`Unknown HarmonyOS product: ${productName}`);
          product.signingConfig = development.name;
          context.setBuildProfileOpt(profile);
        }
      }
    });
  }
};

export default {
  system: appTasks,
  plugins: [developmentBundle]
}
