import { HomeDestination } from '../HomeDestination'

@ComponentV2
export struct HomePhoneChromeTab {
  @Require @Param destination: HomeDestination

  build() {
    Blank()
      .height(0)
      .accessibilityText('home-chrome-' + this.destination)
  }
}
