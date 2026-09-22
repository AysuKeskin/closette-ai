"""Reproduce the planning scenarios; assumptions, not a revenue forecast.

Run: python3 docs/planning/subscription_economics.py --help
No network, credentials, third-party packages or changes to application state.
"""

import argparse
import math


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--fx', type=float, default=50, help='Assumed TRY per USD')
    parser.add_argument('--commission', type=float, default=.15)
    parser.add_argument('--credit-cost', type=float, default=.003, help='USD per credit')
    parser.add_argument('--paid-credits', type=float, default=100)
    parser.add_argument('--free-credits', type=float, default=10)
    parser.add_argument('--new-users', type=int, default=0, help='Welcome grants this month')
    args = parser.parse_args()
    if (not all(math.isfinite(x) for x in [args.fx, args.commission, args.credit_cost,
                                         args.paid_credits, args.free_credits])
            or args.fx <= 0 or not 0 <= args.commission < 1
            or min(args.credit_cost, args.paid_credits, args.free_credits, args.new_users) < 0):
        parser.error('Use finite non-negative inputs, positive FX and commission below 1')

    # TR tax and the US tax-exclusive treatment are budgeting assumptions.
    markets = {'TR': (149.99, 119.99, .20, args.fx),
               'US': (5.99, 4.79, 0, 1)}
    net_normal, net_intro, monthly_contribution, intro_contribution = {}, {}, {}, {}
    free_cost = args.free_credits * args.credit_cost + .02
    paid_cost = args.paid_credits * args.credit_cost + .10
    welcome_cost = args.new_users * 30 * args.credit_cost

    def net(price, tax):
        # Refund reserve 3%; RevenueCat reserve 1% even below its free threshold.
        return price / (1 + tax) * (1 - args.commission) * .97 - .01 * price

    print('Assumptions:', vars(args))
    print('Costs: free ${:.4f}, paid ${:.4f}, welcome grants ${:.2f}'.format(
        free_cost, paid_cost, welcome_cost))
    print('\nMarket | monthly net | first-month intro net | normal USD')
    for region, (monthly, intro, tax, fx) in markets.items():
        nm, ni = net(monthly, tax), net(intro, tax)
        net_normal[region] = nm / fx
        net_intro[region] = ni / fx
        monthly_contribution[region] = nm / fx - paid_cost
        intro_contribution[region] = ni / fx - paid_cost
        print(f'{region} | {nm:.2f} | {ni:.2f} | '
              f'{net_normal[region]:.4f}')

    net_normal['Mixed'] = (net_normal['TR'] + net_normal['US']) / 2
    net_intro['Mixed'] = (net_intro['TR'] + net_intro['US']) / 2
    print('\nMAU | paid share | payers | net receipts USD | costs USD | contribution USD')
    for mau, share, fixed in [(1000, .02, 50), (1000, .05, 50),
                              (1000, .10, 50), (5000, .05, 100)]:
        paid = int(mau * share)
        receipts = paid * net_normal['Mixed']
        costs = paid * paid_cost + (mau - paid) * free_cost + fixed + welcome_cost
        print(f'{mau} | {share:.0%} | {paid} | {receipts:.2f} | {costs:.2f} | '
              f'{receipts - costs:+.2f}')

    print('\nAll 50 payers on their introductory month, 1,000 MAU:')
    print('Contribution USD: {:.2f}'.format(50 * net_intro['Mixed'] - 50 * paid_cost
          - 950 * free_cost - 50 - welcome_cost))

    print('\nBreak-even payers at fixed 1,000 MAU and $50 fixed expense:')
    for name, scenario in [('Normal', net_normal), ('All first-month intro', net_intro)]:
        for region, receipts in scenario.items():
            incremental = receipts - paid_cost + free_cost
            count = math.ceil((50 + 1000 * free_cost + welcome_cost) / incremental) if incremental > 0 else None
            result = str(count) if count is not None and count <= 1000 else 'unreachable'
            print(f'{name}, {region}: {result}')

    print('\nExtra first-month buyers needed for immediate contribution parity:')
    monthly_contribution['Mixed'] = sum(monthly_contribution.values()) / 2
    intro_contribution['Mixed'] = sum(intro_contribution.values()) / 2
    for region, regular in monthly_contribution.items():
        intro = intro_contribution[region]
        uplift = f'{regular / intro - 1:.1%}' if intro > 0 else 'intro loses money per payer'
        print(f'{region}: {uplift}')
    print('\nContribution excludes founder pay, acquisition, company costs and income taxes.')


if __name__ == '__main__':
    main()
